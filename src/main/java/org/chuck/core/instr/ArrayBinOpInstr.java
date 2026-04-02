package org.chuck.core.instr;

import org.chuck.core.*;

/**
 * Base class for binary operations on ChuckArrays (Complex, Polar, Vec).
 */
public abstract class ArrayBinOpInstr implements ChuckInstr {
    protected final String vecTag;
    protected final int defaultSize;

    protected ArrayBinOpInstr(String vecTag, int defaultSize) {
        this.vecTag = vecTag;
        this.defaultSize = defaultSize;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
        Object rObj = s.reg.popObject();
        Object lObj = s.reg.popObject();
        
        ChuckArray rhs = (rObj instanceof ChuckArray ra) ? ra : new ChuckArray(ChuckType.ARRAY, defaultSize);
        ChuckArray lhs = (lObj instanceof ChuckArray la) ? la : new ChuckArray(ChuckType.ARRAY, defaultSize);
        
        // Result size usually matches lhs size (element-wise or specialized)
        ChuckArray result = new ChuckArray(ChuckType.ARRAY, lhs.size());
        result.vecTag = vecTag;
        
        compute(lhs, rhs, result);
        
        s.reg.pushObject(result);
    }

    protected abstract void compute(ChuckArray lhs, ChuckArray rhs, ChuckArray result);
}
