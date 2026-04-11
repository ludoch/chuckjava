package org.chuck.core;

import java.util.List;

/**
 * Reflect: Object and Shred introspection.
 */
public class Reflect extends ChuckObject {
    public Reflect() {
        super(ChuckType.OBJECT);
    }

    public static int id(ChuckShred s) { return s != null ? s.id() : -1; }
    public static String name(ChuckShred s) { return s != null ? s.getName() : ""; }
    public static int done(ChuckShred s) { return s != null && s.isDone() ? 1 : 0; }
    
    public static String type(Object o) {
        if (o == null) return "null";
        if (o instanceof ChuckObject co) return co.getType().getName();
        return o.getClass().getSimpleName();
    }

    public static String doc(Object o) {
        if (o == null) return "";
        String className = type(o);
        ChuckVM vm = ChuckVM.CURRENT_VM.get();
        if (vm == null) return "";
        UserClassDescriptor desc = vm.getUserClass(className);
        return desc != null && desc.doc() != null ? desc.doc() : "";
    }

    public static String doc(Object o, String member) {
        if (o == null || member == null) return "";
        String className = type(o);
        ChuckVM vm = ChuckVM.CURRENT_VM.get();
        if (vm == null) return "";
        UserClassDescriptor desc = vm.getUserClass(className);
        if (desc == null) return "";
        
        // Check method docs
        // Method key is name:argc. Since we don't know argc here, we might need to search.
        for (String key : desc.methodDocs().keySet()) {
            if (key.startsWith(member + ":")) {
                return desc.methodDocs().get(key);
            }
        }

        // Check field docs
        if (desc.fieldDocs().containsKey(member)) {
            return desc.fieldDocs().get(member);
        }
        
        return "";
    }

    public static String docGlobal(String name) {
        ChuckVM vm = ChuckVM.CURRENT_VM.get();
        if (vm == null) return "";
        String doc = vm.getGlobalDoc(name);
        return doc != null ? doc : "";
    }

    public static String docFunc(String name) {
        ChuckVM vm = ChuckVM.CURRENT_VM.get();
        if (vm == null) return "";
        for (String key : vm.getGlobalFunctionDocKeys()) {
            if (key.startsWith(name + ":")) {
                return vm.getGlobalFunctionDoc(key);
            }
        }
        return "";
    }
}
