/*
 * Copyright (C) 2012-2016 Markus Junginger, greenrobot (http://greenrobot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.greenrobot.eventbus;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
/*在主线程执行回调方法，发送event*/
final class HandlerPoster extends Handler {

    private final PendingPostQueue queue;
    private final int maxMillisInsideHandleMessage;
    private final EventBus eventBus;
    private boolean handlerActive;

    HandlerPoster(EventBus eventBus, Looper looper, int maxMillisInsideHandleMessage) {
        super(looper);
        this.eventBus = eventBus;
        this.maxMillisInsideHandleMessage = maxMillisInsideHandleMessage;
        queue = new PendingPostQueue();
    }

    /**/
    // TODO: 2017/2/25 切换到主线程,将数据存入队列中，并发送消息通知处理(8.1) 
    void enqueue(Subscription subscription, Object event) {
        PendingPost pendingPost = PendingPost.obtainPendingPost(subscription, event);/*封装event,注册类，订阅方法*/
        synchronized (this) {
            queue.enqueue(pendingPost);/*存入队列中*/
            if (!handlerActive) {
                handlerActive = true;
                if (!sendMessage(obtainMessage())) {/*发送消息通知处理*/
                    throw new EventBusException("Could not send handler message");
                }
            }
        }
    }

    // TODO: 2017/2/25 处理消息（8.1.1） 
    @Override
    public void handleMessage(Message msg) {
        boolean rescheduled = false;
        try {
            long started = SystemClock.uptimeMillis();
            while (true) {/*从队列中轮循取出数据*/
                PendingPost pendingPost = queue.poll();
                if (pendingPost == null) {/*无数据*/
                    synchronized (this) {
                        // Check again, this time in synchronized
                        pendingPost = queue.poll();
                        if (pendingPost == null) {
                            handlerActive = false;
                            return;
                        }
                    }
                }
                eventBus.invokeSubscriber(pendingPost);/*通过反射执行回调订阅方法，传递event*/
                long timeInMethod = SystemClock.uptimeMillis() - started;
                // TODO: 2017/2/25 超时会影响什么？ 
                if (timeInMethod >= maxMillisInsideHandleMessage) {/*超时（10毫秒）*/
                    if (!sendMessage(obtainMessage())) {
                        throw new EventBusException("Could not send handler message");
                    }
                    rescheduled = true;
                    return;
                }
            }
        } finally {
            handlerActive = rescheduled;
        }
    }
}