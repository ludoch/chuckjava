package org.chuck.core.instr;

import org.chuck.core.*;

public class ArithmeticInstrs {

  private static double toDouble(Object o) {
    return switch (o) {
      case null -> 0.0;
      case ChuckDuration cd -> cd.samples();
      case Number n -> n.doubleValue();
      default -> 0.0;
    };
  }

  public static class AddAny implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      if (s.reg.getSp() < 2) return;
      if (s.reg.isObject(0) || s.reg.isObject(1)) {
        Object r = s.reg.pop(), l = s.reg.pop();
        if (l instanceof ChuckDuration ld && r instanceof ChuckDuration rd) {
          s.reg.pushObject(ChuckObjectPool.getDuration(ld.samples() + rd.samples()));
        } else if (l instanceof ChuckArray la
            && r instanceof ChuckArray ra
            && "complex".equals(la.vecTag)
            && "complex".equals(ra.vecTag)) {
          s.reg.pushObject(
              new ChuckArray(
                  "complex",
                  new double[] {la.getFloat(0) + ra.getFloat(0), la.getFloat(1) + ra.getFloat(1)}));
        } else if (l instanceof ChuckArray la
            && r instanceof ChuckArray ra
            && "polar".equals(la.vecTag)
            && "polar".equals(ra.vecTag)) {
          double lre = la.getFloat(0) * Math.cos(la.getFloat(1)),
              lim = la.getFloat(0) * Math.sin(la.getFloat(1));
          double rre = ra.getFloat(0) * Math.cos(ra.getFloat(1)),
              rim = ra.getFloat(0) * Math.sin(ra.getFloat(1));
          double sre = lre + rre, sim = lim + rim;
          s.reg.pushObject(
              new ChuckArray(
                  "polar", new double[] {Math.sqrt(sre * sre + sim * sim), Math.atan2(sim, sre)}));
        } else {
          String ls = (l instanceof Double d) ? String.format("%.6f", d) : String.valueOf(l);
          String rs = (r instanceof Double d) ? String.format("%.6f", d) : String.valueOf(r);
          s.reg.pushObject(ChuckObjectPool.getString(ls + rs));
        }
      } else if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
        double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
        s.reg.push(l + r);
      } else {
        long r = s.reg.popLong(), l = s.reg.popLong();
        s.reg.push(l + r);
      }
    }
  }

  public static class MinusAny implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      if (s.reg.getSp() < 2) return;
      if (s.reg.isObject(0) || s.reg.isObject(1)) {
        Object r = s.reg.pop(), l = s.reg.pop();
        if (l instanceof ChuckDuration ld && r instanceof ChuckDuration rd) {
          s.reg.pushObject(ChuckObjectPool.getDuration(ld.samples() - rd.samples()));
        } else if (l instanceof ChuckArray la
            && r instanceof ChuckArray ra
            && "complex".equals(la.vecTag)
            && "complex".equals(ra.vecTag)) {
          s.reg.pushObject(
              new ChuckArray(
                  "complex",
                  new double[] {la.getFloat(0) - ra.getFloat(0), la.getFloat(1) - ra.getFloat(1)}));
        } else if (l instanceof ChuckArray la
            && r instanceof ChuckArray ra
            && "polar".equals(la.vecTag)
            && "polar".equals(ra.vecTag)) {
          double lre = la.getFloat(0) * Math.cos(la.getFloat(1)),
              lim = la.getFloat(0) * Math.sin(la.getFloat(1));
          double rre = ra.getFloat(0) * Math.cos(ra.getFloat(1)),
              rim = ra.getFloat(0) * Math.sin(ra.getFloat(1));
          double sre = lre - rre, sim = lim - rim;
          s.reg.pushObject(
              new ChuckArray(
                  "polar", new double[] {Math.sqrt(sre * sre + sim * sim), Math.atan2(sim, sre)}));
        } else {
          s.reg.push(0.0);
        }
        return;
      }
      if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
        double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
        s.reg.push(l - r);
      } else {
        long r = s.reg.popAsLong(), l = s.reg.popAsLong();
        s.reg.push(l - r);
      }
    }
  }

  public static class TimesAny implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      if (s.reg.getSp() < 2) return;
      if (s.reg.isObject(0) || s.reg.isObject(1)) {
        Object r = s.reg.pop(), l = s.reg.pop();
        if (l instanceof ChuckArray la
            && r instanceof ChuckArray ra
            && "complex".equals(la.vecTag)
            && "complex".equals(ra.vecTag)) {
          double lre = la.getFloat(0), lim = la.getFloat(1);
          double rre = ra.getFloat(0), rim = ra.getFloat(1);
          s.reg.pushObject(
              new ChuckArray(
                  "complex", new double[] {lre * rre - lim * rim, lre * rim + lim * rre}));
        } else if (l instanceof ChuckArray la
            && r instanceof ChuckArray ra
            && "polar".equals(la.vecTag)
            && "polar".equals(ra.vecTag)) {
          s.reg.pushObject(
              new ChuckArray(
                  "polar",
                  new double[] {la.getFloat(0) * ra.getFloat(0), la.getFloat(1) + ra.getFloat(1)}));
        } else {
          s.reg.push(0.0);
        }
        return;
      }
      if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
        double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
        s.reg.push(l * r);
      } else {
        long r = s.reg.popLong(), l = s.reg.popLong();
        s.reg.push(l * r);
      }
    }
  }

  public static class DivideAny implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      if (s.reg.getSp() < 2) return;
      if (s.reg.isObject(0) || s.reg.isObject(1)) {
        Object r = s.reg.pop(), l = s.reg.pop();
        if (l instanceof ChuckArray la
            && r instanceof ChuckArray ra
            && "complex".equals(la.vecTag)
            && "complex".equals(ra.vecTag)) {
          double lre = la.getFloat(0), lim = la.getFloat(1);
          double rre = ra.getFloat(0), rim = ra.getFloat(1);
          double den = rre * rre + rim * rim;
          if (den == 0) s.reg.pushObject(new ChuckArray("complex", new double[] {0, 0}));
          else
            s.reg.pushObject(
                new ChuckArray(
                    "complex",
                    new double[] {(lre * rre + lim * rim) / den, (lim * rre - lre * rim) / den}));
        } else if (l instanceof ChuckArray la
            && r instanceof ChuckArray ra
            && "polar".equals(la.vecTag)
            && "polar".equals(ra.vecTag)) {
          double rmag = ra.getFloat(0);
          if (rmag == 0) s.reg.pushObject(new ChuckArray("polar", new double[] {0, 0}));
          else
            s.reg.pushObject(
                new ChuckArray(
                    "polar",
                    new double[] {la.getFloat(0) / rmag, la.getFloat(1) - ra.getFloat(1)}));
        } else {
          // ChuckDuration / ChuckDuration = float ratio
          double rVal = toDouble(r), lVal = toDouble(l);
          s.reg.push(rVal == 0.0 ? 0.0 : lVal / rVal);
        }
        return;
      }
      if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
        double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
        s.reg.push(l / r);
      } else {
        long r = s.reg.popLong(), l = s.reg.popLong();
        if (r == 0) s.reg.push(0L);
        else s.reg.push(l / r);
      }
    }
  }

  public static class ModuloAny implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      if (s.reg.getSp() < 2) return;
      if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
        double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
        s.reg.push(l % r);
      } else {
        long r = s.reg.popLong(), l = s.reg.popLong();
        if (r == 0) s.reg.push(0L);
        else s.reg.push(l % r);
      }
    }
  }

  public static class NegateAny implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      if (s.reg.getSp() < 1) return;
      if (s.reg.isObject(0)) {
        Object o = s.reg.pop();
        if (o instanceof ChuckDuration cd)
          s.reg.pushObject(ChuckObjectPool.getDuration(-cd.samples()));
        else s.reg.push(0.0);
      } else if (s.reg.isDouble(0)) {
        s.reg.push(-s.reg.popAsDouble());
      } else {
        s.reg.push(-s.reg.popLong());
      }
    }
  }

  public static class BitwiseAndAny implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long r = s.reg.popAsLong(), l = s.reg.popAsLong();
      s.reg.push(l & r);
    }
  }

  public static class BitwiseOrAny implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long r = s.reg.popAsLong(), l = s.reg.popAsLong();
      s.reg.push(l | r);
    }
  }

  public static class AddInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.push(s.reg.popLong() + s.reg.popLong());
    }
  }

  public static class AddFloat implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.push(s.reg.popDouble() + s.reg.popDouble());
    }
  }

  public static class MinusInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long r = s.reg.popLong(), l = s.reg.popLong();
      s.reg.push(l - r);
    }
  }

  public static class MinusFloat implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      double r = s.reg.popDouble(), l = s.reg.popDouble();
      s.reg.push(l - r);
    }
  }

  public static class TimesInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.push(s.reg.popLong() * s.reg.popLong());
    }
  }

  public static class TimesFloat implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.push(s.reg.popDouble() * s.reg.popDouble());
    }
  }

  public static class DivideInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long r = s.reg.popLong(), l = s.reg.popLong();
      s.reg.push(r == 0 ? 0 : l / r);
    }
  }

  public static class DivideFloat implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      double r = s.reg.popDouble(), l = s.reg.popDouble();
      s.reg.push(r == 0 ? 0.0 : l / r);
    }
  }
}
