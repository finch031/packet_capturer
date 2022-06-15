package com.github.capture.utils;

/**
 * @author yusheng
 * @version 1.0.0
 * @datetime 2020-11-05 08:55
 * @description triple
 */
public class Triple<T1, T2, T3> {
    private T1 t1;
    private T2 t2;
    private T3 t3;

    public Triple(T1 t1, T2 t2, T3 t3) {
        this.t1 = t1;
        this.t2 = t2;
        this.t3 = t3;
    }

    public static <A,B,C> Triple<A,B,C> triple(A t1, B t2, C t3) {
        return new Triple<A,B,C>(t1, t2, t3);
    }

    public T1 getFirst() {
        return t1;
    }

    public void updateFirst(T1 t1){
        this.t1 = t1;
    }

    public T2 getSecond() {
        return t2;
    }

    public void updateSecond(T2 t2){
        this.t2 = t2;
    }

    public T3 getThird() {
        return t3;
    }

    public void updateThird(T3 t3){
        this.t3 = t3;
    }

    @Override
    public int hashCode() {
        return (t1 == null ? 0 : t1.hashCode())
                + (t2 == null ? 0 : 31 * t2.hashCode())
                + (t3 == null ? 0 : 527 * t3.hashCode());
    }

    @Override
    public boolean equals(Object o) {
        if(this == o){
            return true;
        }

        if(o == null || getClass() != o.getClass()){
            return false;
        }

        Triple<?,?,?> tr = (Triple<?,?,?>)o;
        if (t1 != null ? !t1.equals(tr.t1) : tr.t1 != null) {
            return false;
        }

        if (t2 != null ? !t2.equals(tr.t2) : tr.t2 != null) {
            return false;
        }

        if (t3 != null ? !t3.equals(tr.t3) : tr.t3 != null) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return "Triple [t1=" + t1 + ", t2=" + t2 + ", t3=" + t3 + "]";
    }
}