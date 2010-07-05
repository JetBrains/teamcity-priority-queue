/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package jetbrains.buildServer.serverSide.priority.controllers;

import jetbrains.buildServer.controllers.ActionMessages;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.priority.PriorityClassImpl;
import jetbrains.buildServer.serverSide.priority.PriorityClass;
import jetbrains.buildServer.serverSide.priority.PriorityClassManager;
import jetbrains.buildServer.web.openapi.ControllerAction;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 * @author dmitry.neverov
 */
public class DetachBuildTypesAction implements ControllerAction {

  private PriorityClassManager myPriorityClassManager;

  public DetachBuildTypesAction(@NotNull final PriorityClassManager pClassManager,
                                @NotNull final PriorityClassActionsController controller) {
    myPriorityClassManager = pClassManager;
    controller.registerAction(this);
  }

  public boolean canProcess(HttpServletRequest request) {
    return request.getParameter("detachBuildTypes") != null;
  }

  public void process(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response, @Nullable final Element ajaxResponse) {
    String priorityClassId = request.getParameter("pClassId");
    PriorityClass priorityClass = myPriorityClassManager.findPriorityClassById(priorityClassId);
    if (priorityClass != null) {
      Set<String> buildTypesIds = getBuildTypeIdsForDetach(request);
      Collection<SBuildType> updatedBuildTypes = priorityClass.getBuildTypes();
      List<SBuildType> buildTypesToRemove = new ArrayList<SBuildType>();
      for (SBuildType bt : updatedBuildTypes) {
        if (buildTypesIds.contains(bt.getBuildTypeId())) {
          buildTypesToRemove.add(bt);
        }
      }

      boolean buildTypesChanged = updatedBuildTypes.removeAll(buildTypesToRemove);

      if (buildTypesChanged) {
        PriorityClassImpl updatedPriorityClass = new PriorityClassImpl(priorityClass.getId(), priorityClass.getName(),
                priorityClass.getDescription(), priorityClass.getPriority(), new HashSet<SBuildType>(updatedBuildTypes));
        myPriorityClassManager.savePriorityClass(updatedPriorityClass);
      }

      if (buildTypesChanged) {
        if (buildTypesToRemove.size() == 1) {
          ActionMessages.getOrCreateMessages(request).addMessage("buildTypesUnassigned", "1 configuration was unassigned from the priority class");
        } else {
          ActionMessages.getOrCreateMessages(request).addMessage("buildTypesUnassigned", "{0} configurations were unassigned from the priority class",
                  String.valueOf(buildTypesToRemove.size()));
        }
      }      
    }    
  }

  private Set<String> getBuildTypeIdsForDetach(@NotNull final HttpServletRequest request) {
    String[] buildTypeIds = request.getParameterValues("unassign");
    if (buildTypeIds != null) {
      return new HashSet<String>(Arrays.asList(buildTypeIds));
    } else {
      return Collections.emptySet();
    }
  }
}
