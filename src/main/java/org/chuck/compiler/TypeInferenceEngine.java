package org.chuck.compiler;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.chuck.core.UGenRegistry;
import org.chuck.core.UserClassDescriptor;

/** Handles type inference for ChucK expressions. */
public class TypeInferenceEngine {

  private final Map<String, UserClassDescriptor> userClassRegistry;
  private final Map<String, String> globalVarTypes;
  private final java.util.Stack<Map<String, String>> localTypeScopes;
  private final Map<String, String> functionReturnTypes;
  private final String currentClass;

  public TypeInferenceEngine(
      Map<String, UserClassDescriptor> userClassRegistry,
      Map<String, String> globalVarTypes,
      java.util.Stack<Map<String, String>> localTypeScopes,
      Map<String, String> functionReturnTypes,
      String currentClass) {
    this.userClassRegistry = userClassRegistry;
    this.globalVarTypes = globalVarTypes;
    this.localTypeScopes = localTypeScopes;
    this.functionReturnTypes = functionReturnTypes;
    this.currentClass = currentClass;
  }

  public String getExprType(ChuckAST.Exp exp) {
    if (exp == null) return null;
    return switch (exp) {
      case ChuckAST.IdExp id -> {
        String type = null;
        for (int i = localTypeScopes.size() - 1; i >= 0; i--) {
          type = localTypeScopes.get(i).get(id.name());
          if (type != null) break;
        }
        if (type == null && currentClass != null) {
          type = getFieldType(currentClass, id.name());
        }
        if (type == null) {
          type = globalVarTypes.get(id.name());
        }
        if (type == null && userClassRegistry.containsKey(id.name())) {
          type = id.name();
        }
        if (type == null
            && (org.chuck.core.ChuckLanguage.CORE_DATA_TYPES.contains(id.name())
                || isKnownUGenType(id.name()))) {
          type = id.name();
        }
        yield type;
      }
      case ChuckAST.IntExp _ -> "int";
      case ChuckAST.FloatExp _ -> "float";
      case ChuckAST.StringExp _ -> "string";
      case ChuckAST.ComplexLit _ -> "complex";
      case ChuckAST.PolarLit _ -> "polar";
      case ChuckAST.ArrayLitExp e -> {
        if (e.elements().isEmpty()) yield "int[]";
        String t0 = getExprType(e.elements().get(0));
        yield (t0 != null ? t0 : "int") + "[]";
      }
      case ChuckAST.DeclExp e -> e.type();
      case ChuckAST.TypeofExp _ -> "Type";
      case ChuckAST.ArrayAccessExp e -> {
        String base = getExprType(e.base());
        if (base != null) {
          for (int i = 0; i < e.indices().size(); i++) {
            if (base.endsWith("[]")) {
              base = base.substring(0, base.length() - 2);
            }
          }
          yield base;
        }
        yield null;
      }
      case ChuckAST.BinaryExp bin -> {
        String lhsType = getExprType(bin.lhs());
        if (lhsType == null) yield null;

        if (lhsType.equals("vec2")
            || lhsType.equals("vec3")
            || lhsType.equals("vec4")
            || lhsType.equals("complex")
            || lhsType.equals("polar")) {
          if (bin.op() == ChuckAST.Operator.EQ
              || bin.op() == ChuckAST.Operator.NEQ
              || bin.op() == ChuckAST.Operator.LT
              || bin.op() == ChuckAST.Operator.LE
              || bin.op() == ChuckAST.Operator.GT
              || bin.op() == ChuckAST.Operator.GE) {
            yield "int";
          }
          if (bin.op() == ChuckAST.Operator.TIMES) {
            String rhsType = getExprType(bin.rhs());
            if (rhsType != null
                && (rhsType.equals("vec2") || rhsType.equals("vec3") || rhsType.equals("vec4"))) {
              yield "float"; // Dot product
            }
          }
          yield lhsType;
        }

        String opSymbol = getOpSymbol(bin.op());
        if (opSymbol != null && userClassRegistry.containsKey(lhsType)) {
          String rhsType = getExprType(bin.rhs());
          List<String> bArgTypes = (rhsType != null) ? List.of(lhsType, rhsType) : List.of(lhsType);
          String fullKey = getMethodKey("__pub_op__" + opSymbol, bArgTypes);
          String privKey = getMethodKey("__op__" + opSymbol, bArgTypes);

          String retType = functionReturnTypes.get(fullKey);
          if (retType == null) retType = functionReturnTypes.get(privKey);

          if (retType == null) {
            retType = functionReturnTypes.get("__pub_op__" + opSymbol + ":2");
            if (retType == null) retType = functionReturnTypes.get("__op__" + opSymbol + ":2");
          }
          if (retType == null) {
            UserClassDescriptor desc = userClassRegistry.get(lhsType);
            if (desc != null) {
              if (desc.methods().containsKey("__pub_op__" + opSymbol + ":1")
                  || desc.methods().containsKey("__op__" + opSymbol + ":1")) {
                retType =
                    (bin.op() == ChuckAST.Operator.EQ
                            || bin.op() == ChuckAST.Operator.NEQ
                            || bin.op() == ChuckAST.Operator.LT
                            || bin.op() == ChuckAST.Operator.LE
                            || bin.op() == ChuckAST.Operator.GT
                            || bin.op() == ChuckAST.Operator.GE)
                        ? "int"
                        : lhsType;
              }
            }
          }
          if (retType != null) yield retType;
        }
        if (bin.op() == ChuckAST.Operator.WRITE_IO
            || (bin.op() == ChuckAST.Operator.LE && isIOType(lhsType))) {
          yield "IO";
        }
        if (bin.op() == ChuckAST.Operator.EQ
            || bin.op() == ChuckAST.Operator.NEQ
            || bin.op() == ChuckAST.Operator.LT
            || bin.op() == ChuckAST.Operator.LE
            || bin.op() == ChuckAST.Operator.GT
            || bin.op() == ChuckAST.Operator.GE) {
          yield "int";
        }
        yield lhsType;
      }
      case ChuckAST.DotExp dot -> {
        String baseType = getExprType(dot.base());
        if (baseType == null
            && dot.base() instanceof ChuckAST.IdExp id
            && userClassRegistry.containsKey(id.name())) {
          baseType = id.name();
        }
        if (baseType == null) yield null;

        if (baseType.equals("complex") || baseType.equals("polar") || baseType.startsWith("vec")) {
          yield "float";
        }

        if (isKnownUGenType(baseType) || isSubclassOfUGen(baseType)) {
          if (Set.of(
                  "freq",
                  "gain",
                  "pan",
                  "width",
                  "phase",
                  "sharpness",
                  "resonance",
                  "cutoff",
                  "noteOn",
                  "noteOff",
                  "keyOn",
                  "keyOff")
              .contains(dot.member())) {
            yield "float";
          }
        }

        UserClassDescriptor desc = userClassRegistry.get(baseType);
        if (desc != null) {
          for (String[] field : desc.fields()) {
            if (field[1].equals(dot.member())) yield field[0];
          }
          for (String[] field : desc.staticFields()) {
            if (field[1].equals(dot.member())) yield field[0];
          }
        }
        if (baseType.endsWith("[]")) {
          if (dot.member().equals("size")
              || dot.member().equals("cap")
              || dot.member().equals("capacity")) yield "int";
        }
        yield null;
      }
      case ChuckAST.CallExp call -> {
        String mName = null;
        String bType = null;
        if (call.base() instanceof ChuckAST.IdExp id) {
          mName = id.name();
        } else if (call.base() instanceof ChuckAST.DotExp dot) {
          mName = dot.member();
          bType = getExprType(dot.base());
          if (bType == null
              && dot.base() instanceof ChuckAST.IdExp bid
              && userClassRegistry.containsKey(bid.name())) {
            bType = bid.name();
          }
        }

        if (mName != null && (bType == null || isKnownUGenType(bType) || isSubclassOfUGen(bType))) {
          if (Set.of(
                  "freq",
                  "gain",
                  "pan",
                  "width",
                  "phase",
                  "sharpness",
                  "resonance",
                  "cutoff",
                  "noteOn",
                  "noteOff",
                  "keyOn",
                  "keyOff")
              .contains(mName)) {
            yield "float";
          }
        }

        if (call.base() instanceof ChuckAST.IdExp id) {
          List<String> argTypes = call.args().stream().map(this::getExprType).toList();
          String key = getMethodKey(id.name(), argTypes);
          String ret = functionReturnTypes.get(key);
          if (ret != null) yield ret;

          String fallbackKey = id.name() + ":" + call.args().size();
          ret = functionReturnTypes.get(fallbackKey);
          if (ret != null) yield ret;

          if (id.name().equals("print") || id.name().equals("chout") || id.name().equals("cherr"))
            yield "void";
        } else if (call.base() instanceof ChuckAST.DotExp dot) {
          String baseType = bType;
          if (baseType != null) {
            List<String> argTypes = call.args().stream().map(this::getExprType).toList();
            String key = resolveMethodKey(baseType, dot.member(), argTypes);

            UserClassDescriptor desc = userClassRegistry.get(baseType);
            if (desc != null) {
              if (dot.member().equals("range") && baseType.equals("Std")) yield "int[]";
            }

            if (baseType.equals("Std") && dot.member().equals("range")) yield "int[]";

            if (baseType.endsWith("[]")) {
              if (dot.member().equals("size")
                  || dot.member().equals("cap")
                  || dot.member().equals("capacity")) yield "int";
            }

            String ret = functionReturnTypes.get(key);
            if (ret != null) yield ret;
          }
        }
        yield null;
      }
      default -> null;
    };
  }

  private String getFieldType(String className, String fieldName) {
    UserClassDescriptor desc = userClassRegistry.get(className);
    while (desc != null) {
      for (String[] field : desc.fields()) {
        if (field[1].equals(fieldName)) return field[0];
      }
      desc = desc.parentName() != null ? userClassRegistry.get(desc.parentName()) : null;
    }
    return null;
  }

  private boolean isKnownUGenType(String type) {
    return UGenRegistry.isRegistered(type)
        || org.chuck.core.ChuckLanguage.CORE_UGENS.contains(type);
  }

  private boolean isSubclassOfUGen(String className) {
    if (className == null) return false;
    if (isKnownUGenType(className))
      return !org.chuck.core.ChuckLanguage.CORE_DATA_TYPES.contains(className);
    UserClassDescriptor d = userClassRegistry.get(className);
    if (d == null) return false;
    return isSubclassOfUGen(d.parentName());
  }

  private boolean isIOType(String type) {
    if (type == null) return false;
    return type.equals("IO")
        || type.equals("FileIO")
        || type.equals("OscOut")
        || type.equals("OscIn");
  }

  private String getMethodKey(String name, List<String> argTypes) {
    StringBuilder sb = new StringBuilder(name).append(":");
    if (argTypes == null || argTypes.isEmpty()) return sb.append("0").toString();
    for (int i = 0; i < argTypes.size(); i++) {
      sb.append(argTypes.get(i));
      if (i < argTypes.size() - 1) sb.append(",");
    }
    return sb.toString();
  }

  private String resolveMethodKey(String className, String mName, List<String> callArgTypes) {
    UserClassDescriptor desc = userClassRegistry.get(className);
    if (desc == null) return mName + ":" + (callArgTypes != null ? callArgTypes.size() : 0);
    String fullKey = getMethodKey(mName, callArgTypes);
    String t = className;
    while (t != null) {
      UserClassDescriptor d = userClassRegistry.get(t);
      if (d == null) break;
      if (d.methods().containsKey(fullKey) || d.staticMethods().containsKey(fullKey))
        return fullKey;
      t = d.parentName();
    }
    return mName + ":" + (callArgTypes != null ? callArgTypes.size() : 0);
  }

  private String getOpSymbol(ChuckAST.Operator op) {
    return switch (op) {
      case PLUS -> "+";
      case MINUS -> "-";
      case TIMES -> "*";
      case DIVIDE -> "/";
      case PERCENT -> "%";
      case LT -> "<";
      case LE -> "<=";
      case GT -> ">";
      case GE -> ">=";
      case EQ -> "==";
      case NEQ -> "!=";
      default -> null;
    };
  }
}
