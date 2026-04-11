package org.chuck.core.instr;

import org.chuck.core.*;

public class LogicInstrs {

  public static class LessThanAny implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      if (s.reg.getSp() < 2) {
        s.reg.push(0L);
        return;
      }
      if (s.reg.isObject(0) || s.reg.isObject(1)) {
        Object r = s.reg.pop(), l = s.reg.pop();
        if (l == null || r == null) {
          s.reg.push(0);
          return;
        }
        s.reg.push(l.toString().compareTo(r.toString()) < 0 ? 1 : 0);
      } else if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
        double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
        s.reg.push(l < r ? 1 : 0);
      } else {
        long r = s.reg.popAsLong(), l = s.reg.popAsLong();
        s.reg.push(l < r ? 1 : 0);
      }
    }
  }

  public static class GreaterThanAny implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      if (s.reg.getSp() < 2) {
        s.reg.push(0L);
        return;
      }
      if (s.reg.isObject(0) || s.reg.isObject(1)) {
        Object r = s.reg.pop(), l = s.reg.pop();
        if (l == null || r == null) {
          s.reg.push(0);
          return;
        }
        s.reg.push(l.toString().compareTo(r.toString()) > 0 ? 1 : 0);
      } else if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
        double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
        s.reg.push(l > r ? 1 : 0);
      } else {
        long r = s.reg.popAsLong(), l = s.reg.popAsLong();
        s.reg.push(l > r ? 1 : 0);
      }
    }
  }

  public static class LessOrEqualAny implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      if (s.reg.getSp() < 2) {
        s.reg.push(0L);
        return;
      }
      if (s.reg.isObject(0) || s.reg.isObject(1)) {
        Object r = s.reg.pop(), l = s.reg.pop();
        if (l == null || r == null) {
          s.reg.push(l == r ? 1 : 0);
          return;
        }
        s.reg.push(l.toString().compareTo(r.toString()) <= 0 ? 1 : 0);
      } else if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
        double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
        s.reg.push(l <= r ? 1 : 0);
      } else {
        long r = s.reg.popAsLong(), l = s.reg.popAsLong();
        s.reg.push(l <= r ? 1 : 0);
      }
    }
  }

  public static class GreaterOrEqualAny implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      if (s.reg.getSp() < 2) {
        s.reg.push(0L);
        return;
      }
      if (s.reg.isObject(0) || s.reg.isObject(1)) {
        Object r = s.reg.pop(), l = s.reg.pop();
        if (l == null || r == null) {
          s.reg.push(l == r ? 1 : 0);
          return;
        }
        s.reg.push(l.toString().compareTo(r.toString()) >= 0 ? 1 : 0);
      } else if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
        double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
        s.reg.push(l >= r ? 1 : 0);
      } else {
        long r = s.reg.popAsLong(), l = s.reg.popAsLong();
        s.reg.push(l >= r ? 1 : 0);
      }
    }
  }

  public static class EqualsAny implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      if (s.reg.getSp() < 2) {
        s.reg.pop();
        s.reg.push(0L);
        return;
      }
      if (s.reg.isObject(0) && s.reg.isObject(1)) {
        Object r = s.reg.popObject(), l = s.reg.popObject();
        if (l == r) s.reg.push(1);
        else if (l == null || r == null) s.reg.push(0);
        else s.reg.push(l.toString().equals(r.toString()) ? 1 : 0);
      } else if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
        double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
        s.reg.push(l == r ? 1 : 0);
      } else {
        long r = s.reg.popAsLong(), l = s.reg.popAsLong();
        s.reg.push(l == r ? 1 : 0);
      }
    }
  }

  public static class NotEqualsAny implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      if (s.reg.getSp() < 2) {
        s.reg.push(1L);
        return;
      }
      if (s.reg.isObject(0) && s.reg.isObject(1)) {
        Object r = s.reg.popObject(), l = s.reg.popObject();
        if (l == r) s.reg.push(0);
        else if (l == null || r == null) s.reg.push(1);
        else s.reg.push(l.toString().equals(r.toString()) ? 0 : 1);
      } else if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
        double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
        s.reg.push(l != r ? 1 : 0);
      } else {
        long r = s.reg.popAsLong(), l = s.reg.popAsLong();
        s.reg.push(l != r ? 1 : 0);
      }
    }
  }

  public static class LogicalAnd implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long r = s.reg.popAsLong(), l = s.reg.popAsLong();
      s.reg.push((l != 0 && r != 0) ? 1L : 0L);
    }
  }

  public static class LogicalOr implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long r = s.reg.popAsLong(), l = s.reg.popAsLong();
      s.reg.push((l != 0 || r != 0) ? 1L : 0L);
    }
  }

  public static class LogicalNot implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long val = s.reg.popAsLong();
      s.reg.push(val == 0 ? 1L : 0L);
    }
  }

  // --- Specialized Integer Comparisons ---

  public static class EqInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.push(s.reg.popLong() == s.reg.popLong() ? 1L : 0L);
    }
  }

  public static class NeqInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.push(s.reg.popLong() != s.reg.popLong() ? 1L : 0L);
    }
  }

  public static class LtInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long r = s.reg.popLong(), l = s.reg.popLong();
      s.reg.push(l < r ? 1L : 0L);
    }
  }

  public static class LeInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long r = s.reg.popLong(), l = s.reg.popLong();
      s.reg.push(l <= r ? 1L : 0L);
    }
  }

  public static class GtInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long r = s.reg.popLong(), l = s.reg.popLong();
      s.reg.push(l > r ? 1L : 0L);
    }
  }

  public static class GeInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long r = s.reg.popLong(), l = s.reg.popLong();
      s.reg.push(l >= r ? 1L : 0L);
    }
  }

  // --- Specialized Float Comparisons ---

  public static class EqFloat implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.push(s.reg.popDouble() == s.reg.popDouble() ? 1L : 0L);
    }
  }

  public static class NeqFloat implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.push(s.reg.popDouble() != s.reg.popDouble() ? 1L : 0L);
    }
  }

  public static class LtFloat implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      double r = s.reg.popDouble(), l = s.reg.popDouble();
      s.reg.push(l < r ? 1L : 0L);
    }
  }

  public static class LeFloat implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      double r = s.reg.popDouble(), l = s.reg.popDouble();
      s.reg.push(l <= r ? 1L : 0L);
    }
  }

  public static class GtFloat implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      double r = s.reg.popDouble(), l = s.reg.popDouble();
      s.reg.push(l > r ? 1L : 0L);
    }
  }

  public static class GeFloat implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      double r = s.reg.popDouble(), l = s.reg.popDouble();
      s.reg.push(l >= r ? 1L : 0L);
    }
  }
}
