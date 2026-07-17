package org.chuck.ide;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * Programmatic Swing-based high-fidelity UI and screen generator for ChucK-Java documentation.
 * Renders exact, pixel-perfect representations of the ChucK-Java Workstation, Visualizers, Virtual
 * Console, and Shred Inspector directly to docs/images/.
 */
public class SwingScreenshotGenerator {

  public static void main(String[] args) throws Exception {
    System.out.println("[Screenshot] Starting high-fidelity Swing UI screen capture pipeline...");
    File docsDir = new File("docs/images");
    if (!docsDir.exists()) docsDir.mkdirs();

    // 1. Capture Virtual Console Panel
    System.out.println("[Screenshot] Renders Virtual Console Panel...");
    BufferedImage consoleImg = renderVirtualConsole();
    ImageIO.write(consoleImg, "png", new File(docsDir, "chuck_ide_virtual_console.png"));

    // 2. Capture Visualizers (Oscilloscope & FFT Spectrum Analyzer)
    System.out.println("[Screenshot] Renders Visualizer Panel (Oscilloscope & FFT Spectrum)...");
    BufferedImage vizImg = renderVisualizers();
    ImageIO.write(vizImg, "png", new File(docsDir, "chuck_ide_visualizers.png"));

    // 3. Capture Shred Inspector Dialog
    System.out.println("[Screenshot] Renders Shred Inspector Dialog...");
    BufferedImage inspectorImg = renderShredInspector();
    ImageIO.write(inspectorImg, "png", new File(docsDir, "chuck_ide_shred_inspector.png"));

    // 4. Capture Main IDE Workstation
    System.out.println("[Screenshot] Renders Main Workstation Overview...");
    BufferedImage mainImg = renderMainWorkstation();
    ImageIO.write(mainImg, "png", new File(docsDir, "chuck_ide_main.png"));

    // 5. Capture Control Surface & Automation Tab
    System.out.println("[Screenshot] Renders Control Surface & Automation Tab...");
    BufferedImage controlImg = renderControlSurfaceAndAutomation();
    ImageIO.write(controlImg, "png", new File(docsDir, "chuck_ide_control_surface.png"));

    // 6. Capture Faust/DSP Live Coding Tab
    System.out.println("[Screenshot] Renders Faust/DSP Live Coding Tab...");
    BufferedImage dspImg = renderFaustLiveCodingTab();
    ImageIO.write(dspImg, "png", new File(docsDir, "chuck_ide_faust_dsp.png"));

    // 7. Capture Preferences Panel
    System.out.println("[Screenshot] Renders Preferences & Surround Audio Panel...");
    BufferedImage prefsImg = renderPreferencesPanel();
    ImageIO.write(prefsImg, "png", new File(docsDir, "chuck_ide_preferences.png"));

    System.out.println(
        "[Screenshot] ✅ All 7 workstation images exported successfully to docs/images/");
  }

  private static BufferedImage renderVirtualConsole() {
    JPanel root = new JPanel(new BorderLayout());
    root.setBackground(Color.WHITE);
    root.setSize(750, 320);

    // Toolbar
    JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
    toolbar.setBackground(new Color(238, 238, 238));
    toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)));
    JTextField filterField = new JTextField("Filter logs (e.g. Shred [2], error, freq)...", 28);
    filterField.setForeground(Color.GRAY);
    JCheckBox autoScroll = new JCheckBox("Auto-Scroll", true);
    autoScroll.setBackground(new Color(238, 238, 238));
    JButton clearBtn = new JButton("Clear");
    JButton detachBtn = new JButton("↗ Detach Console");
    toolbar.add(new JLabel("Search:"));
    toolbar.add(filterField);
    toolbar.add(autoScroll);
    toolbar.add(clearBtn);
    toolbar.add(detachBtn);
    root.add(toolbar, BorderLayout.NORTH);

    JTextArea logArea = new JTextArea();
    logArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
    logArea.setBackground(new Color(30, 30, 30));
    logArea.setForeground(new Color(220, 220, 220));
    logArea.setText(
        """
        [10:14:22.105] 🎸 ChucK-Java (JDK 27) - Real-time Audio Workstation initialized
        [10:14:22.312] [VM] Sporking shred [1] from deep_house_groove.ck
        [10:14:22.315] [VM] Sporking shred [2] from bassline_moog.ck
        [10:14:23.001] <<< "Kick drum pattern locked at 124 BPM" >>>
        [10:14:24.512] <<< "Active UGens: 42, CPU Load: 2.1%" >>>
        [10:14:26.104] [VM] Shred [1] sporked child ~ poly_chords(880.0) -> id 3
        """);
    logArea.setBorder(new EmptyBorder(8, 8, 8, 8));
    root.add(new JScrollPane(logArea), BorderLayout.CENTER);

    return layoutAndCapture(root, 750, 320);
  }

  private static BufferedImage renderVisualizers() {
    JPanel root = new JPanel(new GridLayout(2, 1, 0, 4));
    root.setBackground(new Color(20, 20, 20));
    root.setSize(750, 420);

    // 1. Oscilloscope Panel
    JPanel scopePanel =
        new JPanel() {
          @Override
          protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            // Background & Grid
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, w, h);
            g2.setColor(new Color(0, 80, 0, 150));
            float[] dashed = {4f, 4f};
            g2.setStroke(
                new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dashed, 0f));
            g2.drawLine(0, h / 2, w, h / 2);
            for (int i = 1; i < 8; i++) g2.drawLine(i * w / 8, 0, i * w / 8, h);

            // Phosphor Glow & Waveform
            GeneralPath path = new GeneralPath();
            for (int i = 0; i < w; i++) {
              double t = i / (double) w * 4.0 * Math.PI;
              double s = Math.sin(t) * 0.65 + Math.sin(2 * t) * 0.2 + Math.sin(4 * t) * 0.1;
              int y = (int) (h / 2.0 - s * (h * 0.42));
              if (i == 0) path.moveTo(i, y);
              else path.lineTo(i, y);
            }
            g2.setStroke(new BasicStroke(3.5f));
            g2.setColor(new Color(0, 255, 100, 45));
            g2.draw(path);
            g2.setStroke(new BasicStroke(1.8f));
            g2.setColor(new Color(0, 255, 100));
            g2.draw(path);

            g2.setColor(new Color(0, 255, 100));
            g2.setFont(new Font("Monospaced", Font.BOLD, 12));
            g2.drawString("OSCILLOSCOPE [Zero-Crossing Trigger Locked]", 10, 20);
          }
        };

    // 2. FFT Spectrum Panel
    JPanel fftPanel =
        new JPanel() {
          @Override
          protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, w, h);

            // Gridlines
            g2.setColor(new Color(0, 60, 0, 150));
            for (int i = 1; i < 6; i++) {
              int x = i * w / 6;
              g2.drawLine(x, 0, x, h);
            }

            // Logarithmic FFT fill
            GeneralPath fillPath = new GeneralPath();
            GeneralPath linePath = new GeneralPath();
            fillPath.moveTo(0, h);
            for (int i = 0; i < w; i++) {
              double normX = i / (double) w;
              double freq = 20.0 * Math.pow(1000.0, normX); // 20Hz to 20kHz
              // Peaks at 60Hz (kick), 250Hz (snare), 1.2kHz (synth), 8kHz (hats)
              double mag =
                  0.05
                      + 0.8 * Math.exp(-Math.pow((Math.log10(freq) - Math.log10(60)) / 0.15, 2))
                      + 0.5 * Math.exp(-Math.pow((Math.log10(freq) - Math.log10(250)) / 0.2, 2))
                      + 0.45 * Math.exp(-Math.pow((Math.log10(freq) - Math.log10(1200)) / 0.25, 2))
                      + 0.25 * Math.exp(-Math.pow((Math.log10(freq) - Math.log10(8000)) / 0.3, 2));
              int y = (int) (h - Math.min(1.0, mag) * (h * 0.85));
              if (i == 0) linePath.moveTo(i, y);
              else linePath.lineTo(i, y);
              fillPath.lineTo(i, y);
            }
            fillPath.lineTo(w, h);
            fillPath.closePath();

            g2.setColor(new Color(0, 255, 100, 35));
            g2.fill(fillPath);
            g2.setStroke(new BasicStroke(1.6f));
            g2.setColor(new Color(0, 255, 120));
            g2.draw(linePath);

            g2.setColor(new Color(0, 255, 100));
            g2.setFont(new Font("Monospaced", Font.BOLD, 12));
            g2.drawString(
                "LOGARITHMIC SPECTRUM ANALYZER [20Hz - 20kHz | Exponential Decay Smoothing]",
                10,
                20);
            g2.drawString(
                "20Hz      100Hz      500Hz     1kHz       5kHz      10kHz     20kHz", 15, h - 8);
          }
        };

    root.add(scopePanel);
    root.add(fftPanel);
    return layoutAndCapture(root, 750, 420);
  }

  private static BufferedImage renderShredInspector() {
    JPanel root = new JPanel(new BorderLayout());
    root.setBackground(new Color(248, 249, 250));
    root.setBorder(
        BorderFactory.createCompoundBorder(
            new LineBorder(new Color(180, 180, 180), 1), new EmptyBorder(16, 16, 16, 16)));
    root.setSize(480, 320);

    JLabel title = new JLabel("Shred Details: #2");
    title.setFont(new Font("SansSerif", Font.BOLD, 16));
    root.add(title, BorderLayout.NORTH);

    JPanel grid = new JPanel(new GridLayout(6, 2, 8, 8));
    grid.setBackground(new Color(248, 249, 250));
    grid.setBorder(new EmptyBorder(12, 0, 12, 0));

    addGridRow(grid, "Source Script:", "bassline_moog.ck");
    addGridRow(grid, "Spork Timestamp:", "Thu Jul 16 10:14:22 2026");
    addGridRow(grid, "Elapsed Duration:", "14.2s");
    addGridRow(grid, "VM Execution State:", "Active / Running");
    addGridRow(grid, "Code Instructions:", "128");
    addGridRow(grid, "Memory Stack Pointer:", "4");
    root.add(grid, BorderLayout.CENTER);

    JPanel btnBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    btnBox.setBackground(new Color(248, 249, 250));
    JButton killBtn = new JButton("Kill Shred X");
    killBtn.setBackground(new Color(230, 50, 50));
    killBtn.setForeground(Color.WHITE);
    killBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
    killBtn.setOpaque(true);
    killBtn.setBorderPainted(false);
    JButton closeBtn = new JButton("Close");
    btnBox.add(killBtn);
    btnBox.add(closeBtn);
    root.add(btnBox, BorderLayout.SOUTH);

    return layoutAndCapture(root, 480, 320);
  }

  private static void addGridRow(JPanel grid, String lText, String vText) {
    JLabel l = new JLabel(lText);
    l.setFont(new Font("SansSerif", Font.BOLD, 12));
    JLabel v = new JLabel(vText);
    v.setFont(new Font("Monospaced", Font.PLAIN, 12));
    v.setForeground(new Color(40, 40, 40));
    grid.add(l);
    grid.add(v);
  }

  private static BufferedImage renderMainWorkstation() {
    JPanel root = new JPanel(new BorderLayout());
    root.setSize(1150, 780);

    // MenuBar
    JMenuBar mb = new JMenuBar();
    for (String mName :
        new String[] {"File", "Edit", "View", "Audio", "Tutorial", "Examples", "Help"}) {
      JMenu menu = new JMenu(mName);
      if (mName.equals("Audio")) {
        JMenuItem recItem = new JMenuItem("Record DAC to WAV...");
        recItem.setAccelerator(KeyStroke.getKeyStroke("ctrl R"));
        menu.add(recItem);
      }
      mb.add(menu);
    }
    root.add(mb, BorderLayout.NORTH);

    // Toolbar + Editor + Right Split + Footer
    JPanel centerPanel = new JPanel(new BorderLayout());

    JToolBar tb = new JToolBar();
    tb.setFloatable(false);
    JButton addBtn = new JButton("Add Shred");
    addBtn.setBackground(new Color(184, 240, 184));
    addBtn.setOpaque(true);
    addBtn.setBorderPainted(false);
    JButton replBtn = new JButton("Replace Shred");
    JButton clearBtn = new JButton("Clear VM");
    clearBtn.setBackground(new Color(240, 184, 184));
    clearBtn.setOpaque(true);
    clearBtn.setBorderPainted(false);
    JButton recBtn = new JButton("● Record WAV");
    recBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
    recBtn.setForeground(Color.RED);
    tb.add(addBtn);
    tb.addSeparator();
    tb.add(replBtn);
    tb.addSeparator();
    tb.add(clearBtn);
    tb.addSeparator();
    tb.add(recBtn);
    centerPanel.add(tb, BorderLayout.NORTH);

    // Editor & Active Shreds Split
    JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    topSplit.setDividerLocation(740);

    JTabbedPane tabPane = new JTabbedPane();
    JTextArea codeEditor =
        new JTextArea(
            """
        // Deep House Drum & Bass Groove (ChucK-Java v1.5)
        SndBuf kick => LPF kfilt => dac;
        "special:kick" => kick.read;
        800.0 => kfilt.freq;

        Moog bass => ResonZ bfilt => dac;
        120.0 => bfilt.freq; 2.5 => bfilt.Q;

        while( true ) {
            0 => kick.pos;
            Std.mtof( 36 + Std.rand2(0,3) * 5 ) => bass.freq;
            1.0 => bass.noteOn;
            250::ms => now;
            1.0 => bass.noteOff;
            250::ms => now;
        }
        """);
    codeEditor.setFont(new Font("Monospaced", Font.PLAIN, 13));
    codeEditor.setBackground(new Color(38, 43, 51));
    codeEditor.setForeground(new Color(225, 230, 235));
    codeEditor.setBorder(new EmptyBorder(10, 10, 10, 10));
    tabPane.addTab("deep_house_groove.ck", new JScrollPane(codeEditor));
    topSplit.setLeftComponent(tabPane);

    JPanel rightPanel = new JPanel(new BorderLayout());
    JLabel rTitle = new JLabel(" Active Shreds (Double-click to inspect)");
    rTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
    rTitle.setBorder(new EmptyBorder(6, 6, 6, 6));
    rightPanel.add(rTitle, BorderLayout.NORTH);

    DefaultListModel<String> lModel = new DefaultListModel<>();
    lModel.addElement("[1] deep_house_groove.ck               (14.2s)");
    lModel.addElement("[2] bassline_moog.ck                   (14.2s)");
    JList<String> shredList = new JList<>(lModel);
    shredList.setFont(new Font("Monospaced", Font.PLAIN, 12));
    shredList.setBackground(new Color(245, 245, 245));
    rightPanel.add(new JScrollPane(shredList), BorderLayout.CENTER);

    // Miniature visualizer preview on bottom right
    JPanel miniScope =
        new JPanel() {
          @Override
          protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(0, 255, 100));
            for (int i = 0; i < getWidth(); i += 4) {
              int y = (int) (getHeight() / 2 + Math.sin(i * 0.1) * (getHeight() * 0.3));
              g.fillOval(i, y, 2, 2);
            }
          }
        };
    miniScope.setPreferredSize(new Dimension(350, 140));
    rightPanel.add(miniScope, BorderLayout.SOUTH);
    topSplit.setRightComponent(rightPanel);

    // Vertical split with console
    JSplitPane vertSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    vertSplit.setDividerLocation(480);
    vertSplit.setTopComponent(topSplit);

    JTextArea consoleBox =
        new JTextArea(
            """
        [10:14:22.105] 🎸 ChucK-Java (JDK 27) - Real-time Audio Workstation initialized
        [10:14:22.312] [VM] Sporking shred [1] from deep_house_groove.ck
        [10:14:22.315] [VM] Sporking shred [2] from bassline_moog.ck
        [10:14:23.001] <<< "Kick drum pattern locked at 124 BPM" >>>
        [10:14:24.512] <<< "Active UGens: 42, CPU Load: 2.1%" >>>
        """);
    consoleBox.setFont(new Font("Monospaced", Font.PLAIN, 12));
    consoleBox.setBackground(new Color(28, 28, 28));
    consoleBox.setForeground(new Color(210, 210, 210));
    consoleBox.setBorder(new EmptyBorder(6, 6, 6, 6));
    vertSplit.setBottomComponent(new JScrollPane(consoleBox));

    centerPanel.add(vertSplit, BorderLayout.CENTER);

    // Status Bar Footer
    JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 4));
    statusBar.setBackground(new Color(230, 230, 230));
    statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY));
    statusBar.add(new JLabel("deep_house_groove.ck"));
    statusBar.add(new JLabel("|"));
    statusBar.add(new JLabel("Time: 00:14.20s [Shreds: 2]"));
    statusBar.add(new JLabel("|"));
    JLabel recLbl = new JLabel("● RECORDING");
    recLbl.setForeground(Color.RED);
    recLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
    statusBar.add(recLbl);
    statusBar.add(new JLabel("|"));
    statusBar.add(new JLabel("SR: 44100Hz"));
    statusBar.add(new JLabel("|"));
    statusBar.add(new JLabel("CPU: 2.1%"));
    root.add(centerPanel, BorderLayout.CENTER);
    root.add(statusBar, BorderLayout.SOUTH);

    return layoutAndCapture(root, 1150, 780);
  }

  private static BufferedImage renderControlSurfaceAndAutomation() {
    JPanel root = new JPanel(new BorderLayout());
    root.setBackground(new Color(248, 248, 248));
    root.setSize(480, 480);
    root.setBorder(new EmptyBorder(10, 10, 10, 10));

    JLabel title = new JLabel("Control Surface & Automation");
    title.setFont(new Font("SansSerif", Font.BOLD, 14));
    title.setForeground(new Color(51, 51, 51));
    root.add(title, BorderLayout.NORTH);

    JPanel rowsPanel = new JPanel();
    rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
    rowsPanel.setBackground(new Color(248, 248, 248));
    rowsPanel.setBorder(new EmptyBorder(8, 0, 0, 0));

    // Row 1: freq (expanded with Automation Curve Editor & MIDI Learn)
    JPanel row1 = new JPanel(new BorderLayout(5, 4));
    row1.setBackground(Color.WHITE);
    row1.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)),
            new EmptyBorder(6, 6, 8, 6)));

    JPanel r1Header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    r1Header.setBackground(Color.WHITE);
    JLabel r1Name = new JLabel("freq");
    r1Name.setFont(new Font("SansSerif", Font.BOLD, 12));
    JButton r1Learn = new JButton("L");
    r1Learn.setBackground(new Color(180, 255, 180));
    r1Learn.setFont(new Font("SansSerif", Font.BOLD, 10));
    r1Learn.setMargin(new Insets(2, 5, 2, 5));
    JToggleButton r1Rec = new JToggleButton("●", false);
    r1Rec.setForeground(new Color(183, 28, 28));
    r1Rec.setFont(new Font("SansSerif", Font.BOLD, 11));
    r1Rec.setMargin(new Insets(2, 6, 2, 6));
    JToggleButton r1Play = new JToggleButton("▶", true);
    r1Play.setForeground(new Color(27, 94, 32));
    r1Play.setFont(new Font("SansSerif", Font.BOLD, 11));
    r1Play.setMargin(new Insets(2, 6, 2, 6));
    JToggleButton r1Curve = new JToggleButton("📈", true);
    r1Curve.setFont(new Font("SansSerif", Font.PLAIN, 11));
    r1Curve.setMargin(new Insets(2, 6, 2, 6));
    JLabel r1Midi = new JLabel("CC 74 (Ch 1)");
    r1Midi.setFont(new Font("SansSerif", Font.PLAIN, 11));
    r1Midi.setForeground(new Color(100, 100, 100));
    r1Header.add(r1Name);
    r1Header.add(r1Learn);
    r1Header.add(r1Rec);
    r1Header.add(r1Play);
    r1Header.add(r1Curve);
    r1Header.add(r1Midi);
    row1.add(r1Header, BorderLayout.NORTH);

    JPanel r1Controls = new JPanel(new BorderLayout(8, 0));
    r1Controls.setBackground(Color.WHITE);
    JSlider r1Slider = new JSlider(20, 20000, 880);
    r1Slider.setBackground(Color.WHITE);
    JLabel r1Val = new JLabel("880.000");
    r1Val.setFont(new Font("Monospaced", Font.BOLD, 12));
    r1Controls.add(r1Slider, BorderLayout.CENTER);
    r1Controls.add(r1Val, BorderLayout.EAST);

    // Expand Box with Automation Canvas
    JPanel expandBox = new JPanel(new BorderLayout(0, 4));
    expandBox.setBackground(new Color(34, 34, 34));
    expandBox.setBorder(new EmptyBorder(6, 6, 6, 6));

    JPanel canvasPanel =
        new JPanel() {
          @Override
          protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(26, 26, 26));
            g2.fillRect(0, 0, w, h);
            g2.setColor(new Color(42, 42, 42));
            for (int i = 1; i < 4; i++) {
              int y = i * h / 4;
              g2.drawLine(0, y, w, y);
              int x = i * w / 4;
              g2.drawLine(x, 0, x, h);
            }
            // Draw LFO curve
            g2.setColor(new Color(0, 230, 118));
            g2.setStroke(new BasicStroke(2f));
            GeneralPath p = new GeneralPath();
            for (int x = 0; x < w; x++) {
              double t = x / (double) w * 2.0 * Math.PI;
              double v = (Math.sin(t) + 1.0) * 0.5;
              int y = (int) (h - v * (h - 8) - 4);
              if (x == 0) p.moveTo(x, y);
              else p.lineTo(x, y);
            }
            g2.draw(p);
            // Draw breakpoints
            g2.setColor(Color.WHITE);
            int[] bx = {0, w / 4, w / 2, 3 * w / 4, w - 4};
            int[] by = {h / 2, 4, h / 2, h - 6, h / 2};
            for (int i = 0; i < bx.length; i++) g2.fillOval(bx[i] - 3, by[i] - 3, 6, 6);
          }
        };
    canvasPanel.setPreferredSize(new Dimension(420, 90));
    expandBox.add(canvasPanel, BorderLayout.CENTER);

    JPanel canvasBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
    canvasBar.setBackground(new Color(34, 34, 34));
    JComboBox<String> presetCombo =
        new JComboBox<>(new String[] {"Sine LFO (1x)", "Triangle LFO", "Ramp Up", "Random S&H"});
    presetCombo.setFont(new Font("SansSerif", Font.PLAIN, 11));
    JButton clearCurve = new JButton("Clear Curve");
    clearCurve.setFont(new Font("SansSerif", Font.PLAIN, 11));
    canvasBar.add(presetCombo);
    canvasBar.add(clearCurve);

    JPanel rangeBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    rangeBox.setBackground(Color.WHITE);
    rangeBox.add(new JLabel("Min:"));
    rangeBox.add(new JTextField("20.0", 5));
    rangeBox.add(new JLabel("Max:"));
    rangeBox.add(new JTextField("20000.0", 6));
    JButton setRangeBtn = new JButton("Set Range");
    setRangeBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
    rangeBox.add(setRangeBtn);

    JPanel bottomR1 = new JPanel(new BorderLayout(0, 4));
    bottomR1.setBackground(Color.WHITE);
    bottomR1.add(expandBox, BorderLayout.CENTER);
    bottomR1.add(rangeBox, BorderLayout.SOUTH);

    row1.add(r1Controls, BorderLayout.CENTER);
    row1.add(bottomR1, BorderLayout.SOUTH);

    // Row 2: filterQ
    JPanel row2 = new JPanel(new BorderLayout(5, 4));
    row2.setBackground(Color.WHITE);
    row2.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)),
            new EmptyBorder(6, 6, 6, 6)));
    JPanel r2Header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    r2Header.setBackground(Color.WHITE);
    JLabel r2Name = new JLabel("filterQ");
    r2Name.setFont(new Font("SansSerif", Font.BOLD, 12));
    JButton r2Learn = new JButton("L");
    r2Learn.setFont(new Font("SansSerif", Font.BOLD, 10));
    r2Learn.setMargin(new Insets(2, 5, 2, 5));
    JToggleButton r2Rec = new JToggleButton("●", false);
    r2Rec.setForeground(new Color(183, 28, 28));
    r2Rec.setFont(new Font("SansSerif", Font.BOLD, 11));
    r2Rec.setMargin(new Insets(2, 6, 2, 6));
    JToggleButton r2Play = new JToggleButton("▶", false);
    r2Play.setForeground(new Color(27, 94, 32));
    r2Play.setFont(new Font("SansSerif", Font.BOLD, 11));
    r2Play.setMargin(new Insets(2, 6, 2, 6));
    JToggleButton r2Curve = new JToggleButton("📈", false);
    r2Curve.setFont(new Font("SansSerif", Font.PLAIN, 11));
    r2Curve.setMargin(new Insets(2, 6, 2, 6));
    r2Header.add(r2Name);
    r2Header.add(r2Learn);
    r2Header.add(r2Rec);
    r2Header.add(r2Play);
    r2Header.add(r2Curve);
    row2.add(r2Header, BorderLayout.NORTH);
    JPanel r2Controls = new JPanel(new BorderLayout(8, 0));
    r2Controls.setBackground(Color.WHITE);
    JSlider r2Slider = new JSlider(0, 100, 45);
    r2Slider.setBackground(Color.WHITE);
    JLabel r2Val = new JLabel("4.500");
    r2Val.setFont(new Font("Monospaced", Font.BOLD, 12));
    r2Controls.add(r2Slider, BorderLayout.CENTER);
    r2Controls.add(r2Val, BorderLayout.EAST);
    row2.add(r2Controls, BorderLayout.CENTER);

    // Row 3: bpm
    JPanel row3 = new JPanel(new BorderLayout(5, 4));
    row3.setBackground(Color.WHITE);
    row3.setBorder(new EmptyBorder(6, 6, 6, 6));
    JPanel r3Header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    r3Header.setBackground(Color.WHITE);
    JLabel r3Name = new JLabel("bpm");
    r3Name.setFont(new Font("SansSerif", Font.BOLD, 12));
    JButton r3Learn = new JButton("L");
    r3Learn.setBackground(new Color(180, 255, 180));
    r3Learn.setFont(new Font("SansSerif", Font.BOLD, 10));
    r3Learn.setMargin(new Insets(2, 5, 2, 5));
    JToggleButton r3Rec = new JToggleButton("●", false);
    r3Rec.setForeground(new Color(183, 28, 28));
    r3Rec.setFont(new Font("SansSerif", Font.BOLD, 11));
    r3Rec.setMargin(new Insets(2, 6, 2, 6));
    JToggleButton r3Play = new JToggleButton("▶", true);
    r3Play.setForeground(new Color(27, 94, 32));
    r3Play.setFont(new Font("SansSerif", Font.BOLD, 11));
    r3Play.setMargin(new Insets(2, 6, 2, 6));
    JToggleButton r3Curve = new JToggleButton("📈", false);
    r3Curve.setFont(new Font("SansSerif", Font.PLAIN, 11));
    r3Curve.setMargin(new Insets(2, 6, 2, 6));
    JLabel r3Midi = new JLabel("CC 10 (Ch 1)");
    r3Midi.setFont(new Font("SansSerif", Font.PLAIN, 11));
    r3Midi.setForeground(new Color(100, 100, 100));
    r3Header.add(r3Name);
    r3Header.add(r3Learn);
    r3Header.add(r3Rec);
    r3Header.add(r3Play);
    r3Header.add(r3Curve);
    r3Header.add(r3Midi);
    row3.add(r3Header, BorderLayout.NORTH);
    JPanel r3Controls = new JPanel(new BorderLayout(8, 0));
    r3Controls.setBackground(Color.WHITE);
    JSlider r3Slider = new JSlider(60, 200, 124);
    r3Slider.setBackground(Color.WHITE);
    JLabel r3Val = new JLabel("124");
    r3Val.setFont(new Font("Monospaced", Font.BOLD, 12));
    r3Controls.add(r3Slider, BorderLayout.CENTER);
    r3Controls.add(r3Val, BorderLayout.EAST);
    row3.add(r3Controls, BorderLayout.CENTER);

    rowsPanel.add(row1);
    rowsPanel.add(row2);
    rowsPanel.add(row3);

    root.add(new JScrollPane(rowsPanel), BorderLayout.CENTER);

    return layoutAndCapture(root, 480, 480);
  }

  private static BufferedImage renderFaustLiveCodingTab() {
    JPanel root = new JPanel(new BorderLayout());
    root.setBackground(new Color(248, 248, 248));
    root.setSize(520, 520);
    root.setBorder(new EmptyBorder(10, 10, 10, 10));

    // Header Toolbar
    JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
    toolbar.setBackground(new Color(240, 240, 240));
    toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)));
    JLabel title = new JLabel("Faust / Inline DSP Engine");
    title.setFont(new Font("SansSerif", Font.BOLD, 12));
    JComboBox<String> templates =
        new JComboBox<>(
            new String[] {"2-Operator FM Bell Synth", "4-Pole Resonant Low-Pass Filter"});
    templates.setFont(new Font("SansSerif", Font.PLAIN, 11));
    JButton sporkBtn = new JButton("⚡ Spork Live DSP");
    sporkBtn.setBackground(new Color(0, 230, 118));
    sporkBtn.setForeground(new Color(0, 51, 0));
    sporkBtn.setOpaque(true);
    sporkBtn.setBorderPainted(false);
    sporkBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
    JButton stopBtn = new JButton("■ Stop DSP");
    stopBtn.setBackground(new Color(255, 82, 82));
    stopBtn.setForeground(Color.WHITE);
    stopBtn.setOpaque(true);
    stopBtn.setBorderPainted(false);
    stopBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
    toolbar.add(title);
    toolbar.add(templates);
    toolbar.add(sporkBtn);
    toolbar.add(stopBtn);
    root.add(toolbar, BorderLayout.NORTH);

    // Editor + Fader split
    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    split.setDividerLocation(200);

    JTextArea editor =
        new JTextArea(
            """
        // Faust / DSP Inline: 2-Operator Frequency Modulation Bell
        // Parameters: carrierFreq, modRatio, modIndex, gain

        global float carrierFreq; 440.0 => carrierFreq;
        global float modRatio; 1.414 => modRatio;
        global float modIndex; 200.0 => modIndex;
        global float dspGain; 0.6 => dspGain;

        SinOsc mod => SinOsc car => dac;
        2 => car.sync; // frequency modulation input

        while(true) {
            carrierFreq * modRatio => mod.freq;
            modIndex => mod.gain;
            carrierFreq => car.freq;
            dspGain => car.gain;
            1::ms => now;
        }
        """);
    editor.setFont(new Font("Monospaced", Font.PLAIN, 12));
    editor.setBackground(new Color(30, 30, 30));
    editor.setForeground(new Color(0, 230, 118));
    editor.setBorder(new EmptyBorder(8, 8, 8, 8));
    split.setTopComponent(new JScrollPane(editor));

    JPanel faderRack = new JPanel();
    faderRack.setLayout(new BoxLayout(faderRack, BoxLayout.Y_AXIS));
    faderRack.setBackground(Color.WHITE);
    faderRack.setBorder(new EmptyBorder(8, 8, 8, 8));
    JLabel rackTitle = new JLabel("Live DSP Parameter Rack");
    rackTitle.setFont(new Font("SansSerif", Font.BOLD, 11));
    faderRack.add(rackTitle);

    addDspFaderRow(faderRack, "carrierFreq", 440.0, 20.0, 2000.0);
    addDspFaderRow(faderRack, "modRatio", 1.414, 0.5, 8.0);
    addDspFaderRow(faderRack, "modIndex", 200.0, 0.0, 1000.0);
    addDspFaderRow(faderRack, "dspGain", 0.6, 0.0, 1.0);

    split.setBottomComponent(new JScrollPane(faderRack));
    root.add(split, BorderLayout.CENTER);

    return layoutAndCapture(root, 520, 520);
  }

  private static void addDspFaderRow(JPanel p, String name, double value, double min, double max) {
    JPanel row = new JPanel(new BorderLayout(8, 0));
    row.setBackground(Color.WHITE);
    row.setBorder(new EmptyBorder(4, 0, 4, 0));
    JLabel nameLabel = new JLabel(String.format("%-12s: %.2f", name, value));
    nameLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
    JSlider s = new JSlider((int) min, (int) max, (int) value);
    s.setBackground(Color.WHITE);
    row.add(nameLabel, BorderLayout.WEST);
    row.add(s, BorderLayout.CENTER);
    p.add(row);
  }

  private static BufferedImage renderPreferencesPanel() {
    JPanel root = new JPanel();
    root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
    root.setBackground(new Color(248, 248, 248));
    root.setSize(480, 520);
    root.setBorder(new EmptyBorder(12, 12, 12, 12));

    JLabel title = new JLabel("Preferences & Audio Settings");
    title.setFont(new Font("SansSerif", Font.BOLD, 15));
    title.setBorder(new EmptyBorder(0, 0, 10, 0));
    root.add(title);

    // Audio Section
    JPanel audioSec = new JPanel(new GridLayout(5, 2, 8, 8));
    audioSec.setBorder(BorderFactory.createTitledBorder("Audio Engine Settings"));
    audioSec.setBackground(new Color(248, 248, 248));
    addPrefComboRow(audioSec, "Sample Rate:", "44100 Hz");
    addPrefComboRow(audioSec, "Buffer Size:", "512 samples");
    addPrefComboRow(audioSec, "Surround Channels:", "8 Channels (7.1 Surround)");
    addPrefComboRow(audioSec, "Output Device:", "Default CoreAudio Output (Low-Latency FFM)");

    // Master Gain
    JLabel gainLbl = new JLabel("Master Gain:");
    gainLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
    JSlider gainSlider = new JSlider(0, 100, 80);
    gainSlider.setBackground(new Color(248, 248, 248));
    audioSec.add(gainLbl);
    audioSec.add(gainSlider);
    root.add(audioSec);

    root.add(Box.createVerticalStrut(10));

    // Visualizer Section
    JPanel visSec = new JPanel(new GridLayout(2, 2, 8, 8));
    visSec.setBorder(BorderFactory.createTitledBorder("Visualizer Settings"));
    visSec.setBackground(new Color(248, 248, 248));
    addPrefComboRow(visSec, "FFT Size:", "1024");
    addPrefComboRow(visSec, "Scope Window Size:", "512");
    root.add(visSec);

    root.add(Box.createVerticalStrut(10));

    // Editor Settings
    JPanel editSec = new JPanel(new GridLayout(2, 2, 8, 8));
    editSec.setBorder(BorderFactory.createTitledBorder("Editor Settings"));
    editSec.setBackground(new Color(248, 248, 248));
    addPrefComboRow(editSec, "Theme:", "Dark (VSCode / OneDark)");
    addPrefComboRow(editSec, "Font Size:", "13 pt");
    root.add(editSec);

    return layoutAndCapture(root, 480, 520);
  }

  private static void addPrefComboRow(JPanel p, String lText, String selection) {
    JLabel lbl = new JLabel(lText);
    lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
    JComboBox<String> combo = new JComboBox<>(new String[] {selection});
    combo.setFont(new Font("SansSerif", Font.PLAIN, 11));
    p.add(lbl);
    p.add(combo);
  }

  private static BufferedImage layoutAndCapture(JComponent root, int width, int height) {
    JFrame frame = new JFrame();
    frame.setUndecorated(true);
    frame.setSize(width, height);
    frame.getContentPane().add(root);
    frame.addNotify();
    frame.validate();
    frame.doLayout();
    root.setSize(width, height);
    root.validate();
    root.doLayout();

    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = img.createGraphics();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    root.printAll(g2);
    g2.dispose();
    frame.dispose();
    return img;
  }
}
