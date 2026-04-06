package org.chuck.hid;

import org.chuck.core.ChuckEvent;
import org.chuck.core.ChuckVM;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Represents an HID device (Keyboard, Mouse, etc.).
 * In ChucK, Hid extends Event.
 */
public class Hid extends ChuckEvent {
    private final Deque<HidMsg> queue = new ArrayDeque<>();
    private boolean opened = false;
    private String deviceType = "";

    public Hid() {
        // No explicit type needed if we just use the class
    }

    public int openKeyboard(int index, ChuckVM vm) {
        this.deviceType = "keyboard";
        this.opened = true;
        vm.registerHid(this);
        return 1; // Success
    }

    public int openMouse(int index, ChuckVM vm) {
        this.deviceType = "mouse";
        this.opened = true;
        vm.registerHid(this);
        return 1; // Success
    }

    public int openJoystick(int index, ChuckVM vm) {
        this.deviceType = "joystick";
        this.opened = true;
        
        // Start a polling thread for this joystick
        Thread.ofVirtual().name("Joystick-Poller-" + index).start(() -> {
            int[] lastButtons = {0};
            HidMsg current = new HidMsg();
            while (opened) {
                HidMsg next = new HidMsg();
                next.x = current.x;
                next.y = current.y;
                if (HidNative.pollJoystick(index, next, lastButtons)) {
                    pushMsg(next);
                    broadcast(vm);
                    current = next;
                }
                try { Thread.sleep(20); } catch (InterruptedException e) { break; } // 50Hz polling
            }
        });
        
        return 1;
    }

    public synchronized void pushMsg(HidMsg msg) {
        if (!opened) return;
        queue.addLast(msg);
    }

    public void dispatch(HidMsg msg, ChuckVM vm) {
        if (msg.deviceType != null && !msg.deviceType.equals(this.deviceType)) return;
        pushMsg(msg);
        broadcast(vm);
    }

    public synchronized boolean recv(HidMsg out) {
        HidMsg next = queue.pollFirst();
        if (next != null) {
            out.type = next.type;
            out.which = next.which;
            out.x = next.x;
            out.y = next.y;
            out.key = next.key;
            out.ascii = next.ascii;
            return true;
        }
        return false;
    }

    public boolean isOpened() { return opened; }
    public String getDeviceType() { return deviceType; }
}
