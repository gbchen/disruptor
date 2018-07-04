/*
 * Copyright 2012 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor.dsl;

/**
 * 指定序列器的生成模式。默认使用MULTI模式
 * 如果确认是在单线程环境下产生Event，应该调整为SINGLE模式，可以显著提高性能。因为不用处理并发下sequence的产生
 * 如果在多线程情况下使用SINGLE模式，将会导致混乱，出现sequence丢失问题
 *
 * Defines producer types to support creation of RingBuffer with correct sequencer and publisher.
 */
public enum ProducerType {
    /**
     * Create a RingBuffer with a single event publisher to the RingBuffer
     */
    SINGLE,

    /**
     * Create a RingBuffer supporting multiple event publishers to the one RingBuffer
     */
    MULTI
}
