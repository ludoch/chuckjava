#!/usr/bin/env python3
"""Add missing per-step automation arrays to BridgeContract.java and SwingDelugeApp.java."""

import re

BRIDGE = r"C:\Users\ludo\a\chuckjava\deluge\src\main\java\org\chuck\deluge\BridgeContract.java"
SWING = r"C:\Users\ludo\a\chuckjava\deluge\src\main\java\org\chuck\deluge\ui\SwingDelugeApp.java"

# ─── 36 new step arrays ───────────────────────────────────────────

NEW_STEP_CONSTANTS = """  // ── Extended per-step automation arrays (81 total) ────────────────
  public static final String G_STEP_VOLUME = "g_step_volume";
  public static final String G_STEP_ENV_0_ATTACK = "g_step_env_0_attack";
  public static final String G_STEP_ENV_0_DECAY = "g_step_env_0_decay";
  public static final String G_STEP_ENV_0_SUSTAIN = "g_step_env_0_sustain";
  public static final String G_STEP_ENV_0_RELEASE = "g_step_env_0_release";
  public static final String G_STEP_ENV_1_ATTACK = "g_step_env_1_attack";
  public static final String G_STEP_ENV_1_DECAY = "g_step_env_1_decay";
  public static final String G_STEP_ENV_1_SUSTAIN = "g_step_env_1_sustain";
  public static final String G_STEP_ENV_1_RELEASE = "g_step_env_1_release";
  public static final String G_STEP_ENV_2_ATTACK = "g_step_env_2_attack";
  public static final String G_STEP_ENV_2_DECAY = "g_step_env_2_decay";
  public static final String G_STEP_ENV_2_SUSTAIN = "g_step_env_2_sustain";
  public static final String G_STEP_ENV_2_RELEASE = "g_step_env_2_release";
  public static final String G_STEP_ENV_3_ATTACK = "g_step_env_3_attack";
  public static final String G_STEP_ENV_3_DECAY = "g_step_env_3_decay";
  public static final String G_STEP_ENV_3_SUSTAIN = "g_step_env_3_sustain";
  public static final String G_STEP_ENV_3_RELEASE = "g_step_env_3_release";
  public static final String G_STEP_LFO_0_RATE = "g_step_lfo_0_rate";
  public static final String G_STEP_LFO_0_DEPTH = "g_step_lfo_0_depth";
  public static final String G_STEP_LFO_1_RATE = "g_step_lfo_1_rate";
  public static final String G_STEP_LFO_1_DEPTH = "g_step_lfo_1_depth";
  public static final String G_STEP_LFO_2_RATE = "g_step_lfo_2_rate";
  public static final String G_STEP_LFO_2_DEPTH = "g_step_lfo_2_depth";
  public static final String G_STEP_LFO_3_RATE = "g_step_lfo_3_rate";
  public static final String G_STEP_LFO_3_DEPTH = "g_step_lfo_3_depth";
  public static final String G_STEP_ARP_RATE = "g_step_arp_rate";
  public static final String G_STEP_ARP_GATE = "g_step_arp_gate";
  public static final String G_STEP_FM_AMOUNT = "g_step_fm_amount";
  public static final String G_STEP_FM_RATIO = "g_step_fm_ratio";
  public static final String G_STEP_MOD_FX_FEEDBACK = "g_step_mod_fx_feedback";
  public static final String G_STEP_COMP_ATTACK = "g_step_comp_attack";
  public static final String G_STEP_COMP_RELEASE = "g_step_comp_release";
  public static final String G_STEP_PORTAMENTO = "g_step_portamento";
  public static final String G_STEP_STUTTER = "g_step_stutter";
  public static final String G_STEP_BITCRUSH = "g_step_bitcrush";
  public static final String G_STEP_SRR = "g_step_srr";"""

# ─── New field declarations ───────────────────────────────────────

NEW_FIELDS = """    final float[] stepVolume = new float[PATTERN_SIZE];
    // Envelope params (4 envs * 4 params = 16)
    final float[] stepEnv0Attack = new float[PATTERN_SIZE];
    final float[] stepEnv0Decay = new float[PATTERN_SIZE];
    final float[] stepEnv0Sustain = new float[PATTERN_SIZE];
    final float[] stepEnv0Release = new float[PATTERN_SIZE];
    final float[] stepEnv1Attack = new float[PATTERN_SIZE];
    final float[] stepEnv1Decay = new float[PATTERN_SIZE];
    final float[] stepEnv1Sustain = new float[PATTERN_SIZE];
    final float[] stepEnv1Release = new float[PATTERN_SIZE];
    final float[] stepEnv2Attack = new float[PATTERN_SIZE];
    final float[] stepEnv2Decay = new float[PATTERN_SIZE];
    final float[] stepEnv2Sustain = new float[PATTERN_SIZE];
    final float[] stepEnv2Release = new float[PATTERN_SIZE];
    final float[] stepEnv3Attack = new float[PATTERN_SIZE];
    final float[] stepEnv3Decay = new float[PATTERN_SIZE];
    final float[] stepEnv3Sustain = new float[PATTERN_SIZE];
    final float[] stepEnv3Release = new float[PATTERN_SIZE];
    // LFO params (4 LFOs * 2 params = 8)
    final float[] stepLfo0Rate = new float[PATTERN_SIZE];
    final float[] stepLfo0Depth = new float[PATTERN_SIZE];
    final float[] stepLfo1Rate = new float[PATTERN_SIZE];
    final float[] stepLfo1Depth = new float[PATTERN_SIZE];
    final float[] stepLfo2Rate = new float[PATTERN_SIZE];
    final float[] stepLfo2Depth = new float[PATTERN_SIZE];
    final float[] stepLfo3Rate = new float[PATTERN_SIZE];
    final float[] stepLfo3Depth = new float[PATTERN_SIZE];
    // Arpeggiator
    final float[] stepArpRate = new float[PATTERN_SIZE];
    final float[] stepArpGate = new float[PATTERN_SIZE];
    // FM
    final float[] stepFmAmount = new float[PATTERN_SIZE];
    final float[] stepFmRatio = new float[PATTERN_SIZE];
    // Mod FX feedback
    final float[] stepModFxFeedback = new float[PATTERN_SIZE];
    // Compressor
    final float[] stepCompAttack = new float[PATTERN_SIZE];
    final float[] stepCompRelease = new float[PATTERN_SIZE];
    // Other
    final float[] stepPortamento = new float[PATTERN_SIZE];
    final float[] stepStutter = new float[PATTERN_SIZE];
    final float[] stepBitcrush = new float[PATTERN_SIZE];
    final float[] stepSrr = new float[PATTERN_SIZE];"""

NEW_INIT = """        stepVolume[i] = 0f;
        stepEnv0Attack[i] = 0f;
        stepEnv0Decay[i] = 0f;
        stepEnv0Sustain[i] = 0f;
        stepEnv0Release[i] = 0f;
        stepEnv1Attack[i] = 0f;
        stepEnv1Decay[i] = 0f;
        stepEnv1Sustain[i] = 0f;
        stepEnv1Release[i] = 0f;
        stepEnv2Attack[i] = 0f;
        stepEnv2Decay[i] = 0f;
        stepEnv2Sustain[i] = 0f;
        stepEnv2Release[i] = 0f;
        stepEnv3Attack[i] = 0f;
        stepEnv3Decay[i] = 0f;
        stepEnv3Sustain[i] = 0f;
        stepEnv3Release[i] = 0f;
        stepLfo0Rate[i] = 0f;
        stepLfo0Depth[i] = 0f;
        stepLfo1Rate[i] = 0f;
        stepLfo1Depth[i] = 0f;
        stepLfo2Rate[i] = 0f;
        stepLfo2Depth[i] = 0f;
        stepLfo3Rate[i] = 0f;
        stepLfo3Depth[i] = 0f;
        stepArpRate[i] = 0f;
        stepArpGate[i] = 0f;
        stepFmAmount[i] = 0f;
        stepFmRatio[i] = 0f;
        stepModFxFeedback[i] = 0f;
        stepCompAttack[i] = 0f;
        stepCompRelease[i] = 0f;
        stepPortamento[i] = 0f;
        stepStutter[i] = 0f;
        stepBitcrush[i] = 0f;
        stepSrr[i] = 0f;"""

NEW_REGISTER = """      vm.setGlobalObject(G_STEP_VOLUME, new ChuckArray(stepVolume));
      vm.setGlobalObject(G_STEP_ENV_0_ATTACK, new ChuckArray(stepEnv0Attack));
      vm.setGlobalObject(G_STEP_ENV_0_DECAY, new ChuckArray(stepEnv0Decay));
      vm.setGlobalObject(G_STEP_ENV_0_SUSTAIN, new ChuckArray(stepEnv0Sustain));
      vm.setGlobalObject(G_STEP_ENV_0_RELEASE, new ChuckArray(stepEnv0Release));
      vm.setGlobalObject(G_STEP_ENV_1_ATTACK, new ChuckArray(stepEnv1Attack));
      vm.setGlobalObject(G_STEP_ENV_1_DECAY, new ChuckArray(stepEnv1Decay));
      vm.setGlobalObject(G_STEP_ENV_1_SUSTAIN, new ChuckArray(stepEnv1Sustain));
      vm.setGlobalObject(G_STEP_ENV_1_RELEASE, new ChuckArray(stepEnv1Release));
      vm.setGlobalObject(G_STEP_ENV_2_ATTACK, new ChuckArray(stepEnv2Attack));
      vm.setGlobalObject(G_STEP_ENV_2_DECAY, new ChuckArray(stepEnv2Decay));
      vm.setGlobalObject(G_STEP_ENV_2_SUSTAIN, new ChuckArray(stepEnv2Sustain));
      vm.setGlobalObject(G_STEP_ENV_2_RELEASE, new ChuckArray(stepEnv2Release));
      vm.setGlobalObject(G_STEP_ENV_3_ATTACK, new ChuckArray(stepEnv3Attack));
      vm.setGlobalObject(G_STEP_ENV_3_DECAY, new ChuckArray(stepEnv3Decay));
      vm.setGlobalObject(G_STEP_ENV_3_SUSTAIN, new ChuckArray(stepEnv3Sustain));
      vm.setGlobalObject(G_STEP_ENV_3_RELEASE, new ChuckArray(stepEnv3Release));
      vm.setGlobalObject(G_STEP_LFO_0_RATE, new ChuckArray(stepLfo0Rate));
      vm.setGlobalObject(G_STEP_LFO_0_DEPTH, new ChuckArray(stepLfo0Depth));
      vm.setGlobalObject(G_STEP_LFO_1_RATE, new ChuckArray(stepLfo1Rate));
      vm.setGlobalObject(G_STEP_LFO_1_DEPTH, new ChuckArray(stepLfo1Depth));
      vm.setGlobalObject(G_STEP_LFO_2_RATE, new ChuckArray(stepLfo2Rate));
      vm.setGlobalObject(G_STEP_LFO_2_DEPTH, new ChuckArray(stepLfo2Depth));
      vm.setGlobalObject(G_STEP_LFO_3_RATE, new ChuckArray(stepLfo3Rate));
      vm.setGlobalObject(G_STEP_LFO_3_DEPTH, new ChuckArray(stepLfo3Depth));
      vm.setGlobalObject(G_STEP_ARP_RATE, new ChuckArray(stepArpRate));
      vm.setGlobalObject(G_STEP_ARP_GATE, new ChuckArray(stepArpGate));
      vm.setGlobalObject(G_STEP_FM_AMOUNT, new ChuckArray(stepFmAmount));
      vm.setGlobalObject(G_STEP_FM_RATIO, new ChuckArray(stepFmRatio));
      vm.setGlobalObject(G_STEP_MOD_FX_FEEDBACK, new ChuckArray(stepModFxFeedback));
      vm.setGlobalObject(G_STEP_COMP_ATTACK, new ChuckArray(stepCompAttack));
      vm.setGlobalObject(G_STEP_COMP_RELEASE, new ChuckArray(stepCompRelease));
      vm.setGlobalObject(G_STEP_PORTAMENTO, new ChuckArray(stepPortamento));
      vm.setGlobalObject(G_STEP_STUTTER, new ChuckArray(stepStutter));
      vm.setGlobalObject(G_STEP_BITCRUSH, new ChuckArray(stepBitcrush));
      vm.setGlobalObject(G_STEP_SRR, new ChuckArray(stepSrr));"""

NEW_ACCESSORS = """  public void setStepVolume(int track, int sidx, double val) { step.stepVolume[track * STEPS + sidx] = (float) val; }
  public void setStepEnv0Attack(int track, int sidx, double val) { step.stepEnv0Attack[track * STEPS + sidx] = (float) val; }
  public void setStepEnv0Decay(int track, int sidx, double val) { step.stepEnv0Decay[track * STEPS + sidx] = (float) val; }
  public void setStepEnv0Sustain(int track, int sidx, double val) { step.stepEnv0Sustain[track * STEPS + sidx] = (float) val; }
  public void setStepEnv0Release(int track, int sidx, double val) { step.stepEnv0Release[track * STEPS + sidx] = (float) val; }
  public void setStepEnv1Attack(int track, int sidx, double val) { step.stepEnv1Attack[track * STEPS + sidx] = (float) val; }
  public void setStepEnv1Decay(int track, int sidx, double val) { step.stepEnv1Decay[track * STEPS + sidx] = (float) val; }
  public void setStepEnv1Sustain(int track, int sidx, double val) { step.stepEnv1Sustain[track * STEPS + sidx] = (float) val; }
  public void setStepEnv1Release(int track, int sidx, double val) { step.stepEnv1Release[track * STEPS + sidx] = (float) val; }
  public void setStepEnv2Attack(int track, int sidx, double val) { step.stepEnv2Attack[track * STEPS + sidx] = (float) val; }
  public void setStepEnv2Decay(int track, int sidx, double val) { step.stepEnv2Decay[track * STEPS + sidx] = (float) val; }
  public void setStepEnv2Sustain(int track, int sidx, double val) { step.stepEnv2Sustain[track * STEPS + sidx] = (float) val; }
  public void setStepEnv2Release(int track, int sidx, double val) { step.stepEnv2Release[track * STEPS + sidx] = (float) val; }
  public void setStepEnv3Attack(int track, int sidx, double val) { step.stepEnv3Attack[track * STEPS + sidx] = (float) val; }
  public void setStepEnv3Decay(int track, int sidx, double val) { step.stepEnv3Decay[track * STEPS + sidx] = (float) val; }
  public void setStepEnv3Sustain(int track, int sidx, double val) { step.stepEnv3Sustain[track * STEPS + sidx] = (float) val; }
  public void setStepEnv3Release(int track, int sidx, double val) { step.stepEnv3Release[track * STEPS + sidx] = (float) val; }
  public void setStepLfo0Rate(int track, int sidx, double val) { step.stepLfo0Rate[track * STEPS + sidx] = (float) val; }
  public void setStepLfo0Depth(int track, int sidx, double val) { step.stepLfo0Depth[track * STEPS + sidx] = (float) val; }
  public void setStepLfo1Rate(int track, int sidx, double val) { step.stepLfo1Rate[track * STEPS + sidx] = (float) val; }
  public void setStepLfo1Depth(int track, int sidx, double val) { step.stepLfo1Depth[track * STEPS + sidx] = (float) val; }
  public void setStepLfo2Rate(int track, int sidx, double val) { step.stepLfo2Rate[track * STEPS + sidx] = (float) val; }
  public void setStepLfo2Depth(int track, int sidx, double val) { step.stepLfo2Depth[track * STEPS + sidx] = (float) val; }
  public void setStepLfo3Rate(int track, int sidx, double val) { step.stepLfo3Rate[track * STEPS + sidx] = (float) val; }
  public void setStepLfo3Depth(int track, int sidx, double val) { step.stepLfo3Depth[track * STEPS + sidx] = (float) val; }
  public void setStepArpRate(int track, int sidx, double val) { step.stepArpRate[track * STEPS + sidx] = (float) val; }
  public void setStepArpGate(int track, int sidx, double val) { step.stepArpGate[track * STEPS + sidx] = (float) val; }
  public void setStepFmAmount(int track, int sidx, double val) { step.stepFmAmount[track * STEPS + sidx] = (float) val; }
  public void setStepFmRatio(int track, int sidx, double val) { step.stepFmRatio[track * STEPS + sidx] = (float) val; }
  public void setStepModFxFeedback(int track, int sidx, double val) { step.stepModFxFeedback[track * STEPS + sidx] = (float) val; }
  public void setStepCompAttack(int track, int sidx, double val) { step.stepCompAttack[track * STEPS + sidx] = (float) val; }
  public void setStepCompRelease(int track, int sidx, double val) { step.stepCompRelease[track * STEPS + sidx] = (float) val; }
  public void setStepPortamento(int track, int sidx, double val) { step.stepPortamento[track * STEPS + sidx] = (float) val; }
  public void setStepStutter(int track, int sidx, double val) { step.stepStutter[track * STEPS + sidx] = (float) val; }
  public void setStepBitcrush(int track, int sidx, double val) { step.stepBitcrush[track * STEPS + sidx] = (float) val; }
  public void setStepSrr(int track, int sidx, double val) { step.stepSrr[track * STEPS + sidx] = (float) val; }"""


def read_crlf(path):
    with open(path, 'r', encoding='utf-8') as f:
        return f.read()


def write_crlf(path, content):
    with open(path, 'w', encoding='utf-8', newline='\r\n') as f:
        f.write(content)


def modify_bridge():
    content = read_crlf(BRIDGE)
    lines = content.splitlines(keepends=True)

    # 1. Add constants after G_STEP_PITCH line (line 134, 0-indexed 133)
    # Find the line `  public static final String G_STEP_PITCH`
    const_insert_line = None
    for i, line in enumerate(lines):
        if 'G_STEP_PITCH' in line and 'public static final String' in line and 'g_step_pitch' in line:
            const_insert_line = i
            break
    assert const_insert_line is not None, "Could not find G_STEP_PITCH constant"

    # Insert new constants after this line
    new_const_lines = NEW_STEP_CONSTANTS.split('\n')
    insert_idx = const_insert_line + 1
    for l in reversed(new_const_lines):
        lines.insert(insert_idx, l + '\n')

    # Re-index: lines have shifted. Find the stepPitch field declaration
    content = ''.join(lines)

    # 2. Add field declarations after final float[] stepPitch (before blank line + initDefaults)
    content = content.replace(
        "    final float[] stepPitch = new float[PATTERN_SIZE];\n\n    void initDefaults() {",
        "    final float[] stepPitch = new float[PATTERN_SIZE];\n" + NEW_FIELDS + "\n\n    void initDefaults() {"
    )

    # 3. Add init defaults after stepPitch init line
    content = content.replace(
        "        stepPitch[i] = 0f;\n      }\n    }\n\n    void register(ChuckVM vm) {",
        "        stepPitch[i] = 0f;\n" + NEW_INIT + "\n      }\n    }\n\n    void register(ChuckVM vm) {"
    )

    # 4. Add register calls after G_STEP_PITCH register line
    content = content.replace(
        "      vm.setGlobalObject(G_STEP_PITCH, new ChuckArray(stepPitch));\n    }\n  }",
        "      vm.setGlobalObject(G_STEP_PITCH, new ChuckArray(stepPitch));\n" + NEW_REGISTER + "\n    }\n  }"
    )

    # 5. Add accessor methods after setStepPitch method
    content = content.replace(
        "  public void setStepPitch(int track, int sidx, double val) { step.stepPitch[track * STEPS + sidx] = (float) val; }",
        "  public void setStepPitch(int track, int sidx, double val) { step.stepPitch[track * STEPS + sidx] = (float) val; }\n" + NEW_ACCESSORS
    )

    write_crlf(BRIDGE, content)
    print("BridgeContract.java updated successfully")


def modify_swing():
    content = read_crlf(SWING)

    # The push automation block starts with "// ── Per-step automation merge ──"
    # We need to add push calls for all 36 new params after the existing push calls.
    # The current push ends with A_PITCH push, so we insert after that.

    # Find the end of the push loop: the closing } of the per-step for loop
    # After the last push, there's a `}` closing the for loop, then `}` closing the block
    new_pushes = """            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_VOLUME, s))
              bridge.setStepVolume(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_VOLUME, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_0_ATTACK, s))
              bridge.setStepEnv0Attack(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_0_ATTACK, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_0_DECAY, s))
              bridge.setStepEnv0Decay(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_0_DECAY, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_0_SUSTAIN, s))
              bridge.setStepEnv0Sustain(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_0_SUSTAIN, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_0_RELEASE, s))
              bridge.setStepEnv0Release(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_0_RELEASE, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_1_ATTACK, s))
              bridge.setStepEnv1Attack(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_1_ATTACK, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_1_DECAY, s))
              bridge.setStepEnv1Decay(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_1_DECAY, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_1_SUSTAIN, s))
              bridge.setStepEnv1Sustain(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_1_SUSTAIN, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_1_RELEASE, s))
              bridge.setStepEnv1Release(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_1_RELEASE, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_2_ATTACK, s))
              bridge.setStepEnv2Attack(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_2_ATTACK, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_2_DECAY, s))
              bridge.setStepEnv2Decay(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_2_DECAY, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_2_SUSTAIN, s))
              bridge.setStepEnv2Sustain(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_2_SUSTAIN, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_2_RELEASE, s))
              bridge.setStepEnv2Release(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_2_RELEASE, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_3_ATTACK, s))
              bridge.setStepEnv3Attack(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_3_ATTACK, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_3_DECAY, s))
              bridge.setStepEnv3Decay(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_3_DECAY, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_3_SUSTAIN, s))
              bridge.setStepEnv3Sustain(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_3_SUSTAIN, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_3_RELEASE, s))
              bridge.setStepEnv3Release(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ENV_3_RELEASE, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_LFO_0_RATE, s))
              bridge.setStepLfo0Rate(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_LFO_0_RATE, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_LFO_0_DEPTH, s))
              bridge.setStepLfo0Depth(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_LFO_0_DEPTH, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_LFO_1_RATE, s))
              bridge.setStepLfo1Rate(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_LFO_1_RATE, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_LFO_1_DEPTH, s))
              bridge.setStepLfo1Depth(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_LFO_1_DEPTH, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_LFO_2_RATE, s))
              bridge.setStepLfo2Rate(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_LFO_2_RATE, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_LFO_2_DEPTH, s))
              bridge.setStepLfo2Depth(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_LFO_2_DEPTH, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_LFO_3_RATE, s))
              bridge.setStepLfo3Rate(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_LFO_3_RATE, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_LFO_3_DEPTH, s))
              bridge.setStepLfo3Depth(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_LFO_3_DEPTH, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ARP_RATE, s))
              bridge.setStepArpRate(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ARP_RATE, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_ARP_GATE, s))
              bridge.setStepArpGate(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_ARP_GATE, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_FM_AMOUNT, s))
              bridge.setStepFmAmount(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_FM_AMOUNT, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_FM_RATIO, s))
              bridge.setStepFmRatio(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_FM_RATIO, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_MOD_FX_FEEDBACK, s))
              bridge.setStepModFxFeedback(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_MOD_FX_FEEDBACK, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_COMP_ATTACK, s))
              bridge.setStepCompAttack(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_COMP_ATTACK, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_COMP_RELEASE, s))
              bridge.setStepCompRelease(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_COMP_RELEASE, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_PORTAMENTO, s))
              bridge.setStepPortamento(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_PORTAMENTO, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_STUTTER_RATE, s))
              bridge.setStepStutter(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_STUTTER_RATE, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_BITCRUSH, s))
              bridge.setStepBitcrush(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_BITCRUSH, s));
            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_SAMPLE_RATE_RED, s))
              bridge.setStepSrr(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_SAMPLE_RATE_RED, s));
"""

    # Insert after the A_PITCH push line
    old = "            if (clip.hasAutomation(org.chuck.deluge.model.AutomationParam.A_PITCH, s))\n              bridge.setStepPitch(engRow, s, clip.getAutomation(org.chuck.deluge.model.AutomationParam.A_PITCH, s));"
    new = old + "\n" + new_pushes.rstrip('\n')
    content = content.replace(old, new)

    write_crlf(SWING, content)
    print("SwingDelugeApp.java updated successfully")


if __name__ == '__main__':
    modify_bridge()
    modify_swing()
