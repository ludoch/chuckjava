package org.chuck.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.chuck.core.ChuckEvent;
import org.chuck.core.ChuckVM;

/** Receives OSC messages over UDP. Extends ChuckEvent so shreds can wait on it. */
public class OscIn extends ChuckEvent implements AutoCloseable {
  private DatagramSocket socket;
  private volatile boolean running = false;
  private final Set<String> addresses = ConcurrentHashMap.newKeySet();
  private final ConcurrentLinkedDeque<OscMsg> messages = new ConcurrentLinkedDeque<>();
  private ChuckVM vm;

  public OscIn() {
    super();
    this.vm = ChuckVM.CURRENT_VM.get();
  }

  public OscIn(ChuckVM vm) {
    super();
    this.vm = vm;
  }

  public void port(int port) {
    if (socket != null && !socket.isClosed()) socket.close();
    try {
      socket = new DatagramSocket(port);
      running = true;
      startListening();
    } catch (IOException e) {
      throw new RuntimeException("Failed to open OSC port " + port, e);
    }
  }

  public int port() {
    if (socket != null) return socket.getLocalPort();
    return 0;
  }

  public void addAddress(String addr) {
    // addr may be like "/test", "/test, i f s", "/test/*, is"
    String path = addr.contains(",") ? addr.substring(0, addr.indexOf(",")).trim() : addr.trim();
    addresses.add(path);
  }

  public void listenAll() {
    addAddress("*");
  }

  public boolean recv(OscMsg msg) {
    OscMsg m = messages.pollFirst();
    if (m != null) {
      msg.copyFrom(m);
      return true;
    }
    return false;
  }

  private void startListening() {
    Thread.ofVirtual()
        .name("OSC-In-Listener")
        .start(
            () -> {
              byte[] buffer = new byte[2048];
              while (running && socket != null && !socket.isClosed()) {
                try {
                  DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                  socket.receive(packet);
                  parseAndDispatch(packet.getData(), packet.getLength());
                } catch (IOException e) {
                  break;
                }
              }
            });
  }

  public void removeAddress(String addr) {
    String path = addr.contains(",") ? addr.substring(0, addr.indexOf(",")).trim() : addr.trim();
    addresses.remove(path);
  }

  public void removeAllAddresses() {
    addresses.clear();
  }

  public Set<String> getAddresses() {
    return addresses;
  }

  private void parseAndDispatch(byte[] data, int length) {
    try {
      ByteBuffer buf = ByteBuffer.wrap(data, 0, length);
      parsePacket(buf);
    } catch (Exception ignored) {
    }
  }

  private void parsePacket(ByteBuffer buf) {
    if (!buf.hasRemaining()) return;
    int startPos = buf.position();
    String firstStr = readOscString(buf);
    if (firstStr == null) return;

    if ("#bundle".equals(firstStr)) {
      if (buf.remaining() >= 8) buf.getLong(); // timetag
      while (buf.hasRemaining() && buf.remaining() >= 4) {
        int elemLen = buf.getInt();
        if (elemLen <= 0 || elemLen > buf.remaining()) break;
        int nextPos = buf.position() + elemLen;
        parsePacket(buf);
        buf.position(nextPos);
      }
      return;
    }

    String address = firstStr;
    if (!address.startsWith("/") || !matchesAnyAddress(address)) return;

    String typeTag = buf.hasRemaining() ? readOscString(buf) : ",";
    if (typeTag == null) typeTag = ",";
    if (!typeTag.startsWith(",")) typeTag = "," + typeTag;

    OscMsg msg = new OscMsg();
    msg.address = address;

    for (int i = 1; i < typeTag.length(); i++) {
      if (!buf.hasRemaining()) break;
      char t = typeTag.charAt(i);
      if (t == ' ') continue;
      switch (t) {
        case 'i' -> msg.addInt(buf.getInt());
        case 'f' -> msg.addFloat(buf.getFloat());
        case 's' -> msg.addString(readOscString(buf));
      }
    }

    messages.addLast(msg);
    ChuckVM targetVm = (this.vm != null) ? this.vm : ChuckVM.CURRENT_VM.get();
    broadcast(targetVm);
  }

  private boolean matchesAnyAddress(String address) {
    for (String pattern : addresses) {
      if (matchesPattern(pattern, address)) return true;
    }
    return false;
  }

  private boolean matchesPattern(String pattern, String address) {
    if (!pattern.contains("*") && !pattern.contains("?")) {
      return pattern.equals(address);
    }
    // Convert OSC glob to Java regex
    String regex = pattern.replace(".", "\\.").replace("*", "[^/]*").replace("?", ".");
    return address.matches(regex);
  }

  private String readOscString(ByteBuffer buf) {
    StringBuilder sb = new StringBuilder();
    while (buf.hasRemaining()) {
      byte b = buf.get();
      if (b == 0) break;
      sb.append((char) b);
    }
    // OSC strings are padded to 4-byte boundaries
    int total = sb.length() + 1; // +1 for the null terminator
    int pad = (4 - total % 4) % 4;
    for (int i = 0; i < pad; i++) if (buf.hasRemaining()) buf.get();
    return sb.toString();
  }

  public void close() {
    running = false;
    if (socket != null) socket.close();
  }
}
