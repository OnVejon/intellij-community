/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.InvocationEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;

public class IdeEventQueueTest extends PlatformTestCase {
  public void testManyEvents() {
    int N = 100000;
    PlatformTestUtil.startPerformanceTest("Event queue dispatch", 10000, () -> {
      UIUtil.dispatchAllInvocationEvents();
      AtomicInteger count = new AtomicInteger();
      for (int i = 0; i < N; i++) {
        SwingUtilities.invokeLater(count::incrementAndGet);
      }
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(N, count.get());
    }).cpuBound().assertTiming();
  }

  public void testKeyboardEventsAreDetected() throws InterruptedException {
    assertTrue(EventQueue.isDispatchThread());

    IdeEventQueue ideEventQueue = IdeEventQueue.getInstance();
    assertSame(ideEventQueue, Toolkit.getDefaultToolkit().getSystemEventQueue());
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

    int posted = ideEventQueue.myKeyboardEventsPosted.get();
    int dispatched = ideEventQueue.myKeyboardEventsDispatched.get();
    KeyEvent pressX = new KeyEvent(new JLabel(), KeyEvent.KEY_PRESSED, 1, InputEvent.ALT_DOWN_MASK, 11, 'x');
    postCarefully(pressX);
    assertEquals(posted+1, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched, ideEventQueue.myKeyboardEventsDispatched.get());
    assertEquals(pressX, dispatchAllInvocationEventsUntilOtherEvent(ideEventQueue));

    assertEquals(posted+1, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched+1, ideEventQueue.myKeyboardEventsDispatched.get());

    // do not react to other events
    AWTEvent ev2 = new ActionEvent(new JLabel(), ActionEvent.ACTION_PERFORMED, "Command");
    postCarefully(ev2);

    assertEquals(posted+1, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched+1, ideEventQueue.myKeyboardEventsDispatched.get());
    assertEquals(ev2, dispatchAllInvocationEventsUntilOtherEvent(ideEventQueue));

    assertEquals(posted+1, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched+1, ideEventQueue.myKeyboardEventsDispatched.get());

    KeyEvent keyRelease = new KeyEvent(new JLabel(), KeyEvent.KEY_RELEASED, 1, InputEvent.ALT_DOWN_MASK, 11, 'x');
    postCarefully(keyRelease);

    assertEquals(posted+2, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched+1, ideEventQueue.myKeyboardEventsDispatched.get());
    assertEquals(keyRelease, dispatchAllInvocationEventsUntilOtherEvent(ideEventQueue));

    assertEquals(posted+2, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched+2, ideEventQueue.myKeyboardEventsDispatched.get());
  }

  private static void postCarefully(AWTEvent event) {
    IdeEventQueue ideEventQueue = IdeEventQueue.getInstance();
    boolean didPosted = ideEventQueue.doPostEvent(event);
    assertTrue("Was not posted: "+event, didPosted);
    boolean mustBeConsumed = event.getID() == ActionEvent.ACTION_PERFORMED;
    assertEquals(mustBeConsumed, ReflectionUtil.getField(AWTEvent.class, event, boolean.class, "consumed").booleanValue());
    assertTrue(ReflectionUtil.getField(AWTEvent.class, event, boolean.class, "isPosted"));
  }

  // need this because everybody can post some crazy stuff to IdeEventQueue, so we have to filter InvocationEvents out
  private static AWTEvent dispatchAllInvocationEventsUntilOtherEvent(IdeEventQueue ideEventQueue) throws InterruptedException {
    while (true) {
      AWTEvent event = PlatformTestUtil.dispatchNextEventIfAny(ideEventQueue);
      if (!(event instanceof InvocationEvent)) return event;
    }
  }
}
