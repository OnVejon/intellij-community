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
package com.intellij.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.sun.jdi.ReferenceType;
import com.intellij.debugger.memory.ui.ClassesTable;
import com.intellij.debugger.memory.ui.InstancesWindow;

public class ShowInstancesFromClassesViewAction extends ShowInstancesAction {
  private static final String POPUP_ELEMENT_LABEL = "Show Instances";

  @Override
  protected boolean isEnabled(AnActionEvent e) {
    return super.isEnabled(e) && getSelectedClass(e) != null;
  }

  @Override
  protected void perform(AnActionEvent e) {
    final Project project = e.getProject();
    final ReferenceType selectedClass = getSelectedClass(e);
    if (project != null && selectedClass != null) {
      final XDebugSession debugSession = XDebuggerManager.getInstance(project).getCurrentSession();
      if (debugSession != null) {
        new InstancesWindow(debugSession, limit -> selectedClass.instances(limit), selectedClass.name()).show();
      }
    }
  }

  @Override
  protected String getLabel() {
    return POPUP_ELEMENT_LABEL;
  }

  @Override
  protected int getInstancesCount(AnActionEvent e) {
    ClassesTable.ReferenceCountProvider countProvider = e.getData(ClassesTable.REF_COUNT_PROVIDER_KEY);
    ReferenceType selectedClass = getSelectedClass(e);
    if (countProvider == null || selectedClass == null) {
      return -1;
    }

    return countProvider.getTotalCount(selectedClass);
  }
}
