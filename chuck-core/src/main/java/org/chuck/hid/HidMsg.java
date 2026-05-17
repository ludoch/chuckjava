package org.chuck.hid;

/** Represents a message from an HID device (keyboard, mouse, etc.). */
public class HidMsg extends org.chuck.core.ChuckObject {
  public String deviceType; // "keyboard", "mouse", etc.
  public int type; // 1: Key Down, 2: Key Up, 3: Mouse Move, etc.
  public int which; // Key code or button index
  public float x; // Mouse X or axis value
  public float y; // Mouse Y
  public int key; // Standardized key code
  public float ascii; // ASCII value if applicable

  // Event Types (Matches ChucK constants)
  public static final int BUTTON_DOWN = 1;
  public static final int BUTTON_UP = 2;
  public static final int MOUSE_MOTION = 3;
  public static final int WHEEL_MOTION = 4;

  public HidMsg() {
    super(new org.chuck.core.ChuckType("HidMsg", org.chuck.core.ChuckType.OBJECT, 0, 0));
  }

  public boolean isButtonDown() { return type == BUTTON_DOWN; }
  public boolean isButtonUp() { return type == BUTTON_UP; }
  public boolean isMouseMotion() { return type == MOUSE_MOTION; }
  public boolean isWheelMotion() { return type == WHEEL_MOTION; }
  public boolean isAxisMotion() { return type == MOUSE_MOTION; } // Alias in ChucK

  public float deltaX() { return x; }
  public float deltaY() { return y; }
  public float x() { return x; }
  public float y() { return y; }
  public float z() { return 0; }
}
