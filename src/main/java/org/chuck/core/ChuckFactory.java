package org.chuck.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.chuck.audio.ChuckUGen;
import org.chuck.chugin.ChuginLoader;
import org.chuck.hid.HidMsg;
import org.chuck.midi.MidiMsg;
import org.chuck.midi.MidiOut;
import org.chuck.network.OscBundle;
import org.chuck.network.OscIn;
import org.chuck.network.OscMsg;
import org.chuck.network.OscOut;

public class ChuckFactory {

    public static ChuckObject instantiateType(String t, int argc, Object[] args, float sr, ChuckVM vm, ChuckShred s, Map<String, UserClassDescriptor> rm) {
        if (t == null) return null;
        if (t.endsWith("[]")) {
            String elemType = t.substring(0, t.length() - 2);
            int size = (argc > 0 && args != null && args.length > 0) ? (int) args[0] : 0;
            ChuckArray arr = new ChuckArray(elemType, size);
            return arr;
        }
        t = t.replaceAll("\\[\\]", "");
        
        if (t.equals("complex")) return new ChuckArray("complex", 2);
        if (t.equals("polar")) return new ChuckArray("polar", 2);
        if (t.equals("vec2")) return new ChuckArray("vec2", 2);
        if (t.equals("vec3")) return new ChuckArray("vec3", 3);
        if (t.equals("vec4")) return new ChuckArray("vec4", 4);

        UserClassDescriptor d = (rm != null && rm.containsKey(t)) ? rm.get(t) : (vm != null ? vm.getUserClass(t) : null);
        if (d != null) {
            UserObject uo = new UserObject(t, d.fields(), d.methods(), extendsEvent(t, rm, vm));
            uo.setTickCode(findMethod(t, "tick:1", rm, vm), s, vm);
            
            if (s != null && vm != null) {
                List<String> hierarchy = new ArrayList<>();
                for (String cls = t; cls != null; ) {
                    UserClassDescriptor desc = (rm != null && rm.containsKey(cls)) ? rm.get(cls) : vm.getUserClass(cls);
                    if (desc == null) break;
                    hierarchy.add(0, cls);
                    cls = desc.parentName();
                }
                for (String cls : hierarchy) {
                    UserClassDescriptor desc = (rm != null && rm.containsKey(cls)) ? rm.get(cls) : vm.getUserClass(cls);
                    if (desc == null) continue;
                    if (desc.preCtorCode() != null) {
                        s.thisStack.push(uo);
                        vm.executeSynchronous(desc.preCtorCode(), s);
                        s.thisStack.pop();
                    } else {
                    }
                    ChuckCode ctorCode = desc.methods().get(cls + ":0");
                    if (ctorCode != null) {
                        s.thisStack.push(uo);
                        vm.executeSynchronous(ctorCode, s);
                        s.thisStack.pop();
                    }
                }
            }
            return uo;
        }

        ChuckUGen ugen = UGenRegistry.instantiate(t, sr, args);
        if (ugen != null) return ugen;

        ChuckObject chugin = ChuginLoader.instantiateChugin(t, sr, vm);
        if (chugin != null) return chugin;

        return switch (t) {
            case "string" -> new ChuckString("");
            case "vec2" -> new ChuckArray("vec2", 2);
            case "vec3" -> new ChuckArray("vec3", 3);
            case "vec4" -> new ChuckArray("vec4", 4);
            case "complex" -> new ChuckArray("complex", 2);
            case "polar" -> new ChuckArray("polar", 2);
            case "MidiIn" -> new org.chuck.midi.MidiIn(vm);
            case "MidiOut" -> new MidiOut();
            case "Hid" -> new org.chuck.hid.Hid();
            case "SerialIO" -> new SerialIO();
            case "OscBundle" -> new OscBundle();
            case "RegEx" -> new RegEx();
            case "Reflect" -> new Reflect();
            case "FileIO" -> new FileIO();
            case "StringTokenizer" -> new StringTokenizer();
            case "Object" -> new ChuckObject(ChuckType.OBJECT);
            case "Event" -> new ChuckEvent();
            case "OscIn" -> new OscIn(vm);
            case "OscOut" -> new OscOut();
            case "OscMsg" -> new OscMsg();
            case "MidiMsg" -> new MidiMsg();
            case "HidMsg" -> new HidMsg();
            default -> null;
        };
    }

    private static boolean extendsEvent(String t, Map<String, UserClassDescriptor> rm, ChuckVM vm) {
        for (int depth = 0; depth < 16 && t != null; depth++) {
            if ("Event".equals(t)) return true;
            UserClassDescriptor d = (rm != null && rm.containsKey(t)) ? rm.get(t) : (vm != null ? vm.getUserClass(t) : null);
            if (d == null) return false;
            t = d.parentName();
        }
        return false;
    }

    private static ChuckCode findMethod(String className, String methodName, Map<String, UserClassDescriptor> rm, ChuckVM vm) {
        String t = className;
        for (int depth = 0; depth < 16 && t != null; depth++) {
            UserClassDescriptor d = (rm != null && rm.containsKey(t)) ? rm.get(t) : (vm != null ? vm.getUserClass(t) : null);
            if (d == null) break;
            if (d.methods().containsKey(methodName)) return d.methods().get(methodName);
            t = d.parentName();
        }
        return null;
    }

    public static ChuckArray buildMultiDimArray(long[] dims, int dimIdx, String elemType, ChuckVM vm, ChuckShred s, Map<String, UserClassDescriptor> rm) {
        int sz = (int) dims[dimIdx];
        ChuckArray arr = new ChuckArray(ChuckType.ARRAY, sz);
        if (dimIdx + 1 < dims.length) {
            for (int i = 0; i < sz; i++) {
                arr.setObject(i, buildMultiDimArray(dims, dimIdx + 1, elemType, vm, s, rm));
            }
        } else {
            for (int i = 0; i < sz; i++) {
                ChuckObject elem = instantiateType(elemType, 0, null, vm.getSampleRate(), vm, s, rm);
                if (elem != null) {
                    arr.setObject(i, elem);
                    if (elem instanceof ChuckUGen u) s.registerUGen(u);
                }
            }
        }
        return arr;
    }
}
