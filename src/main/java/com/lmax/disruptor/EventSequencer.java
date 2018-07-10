package com.lmax.disruptor;

/**
 * 序号管理
 */
public interface EventSequencer<T> extends DataProvider<T>, Sequenced {

}
