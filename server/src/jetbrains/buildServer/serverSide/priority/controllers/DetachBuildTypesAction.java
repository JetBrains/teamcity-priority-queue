

package jetbrains.buildServer.serverSide.priority.controllers;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.ActionMessages;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.priority.PriorityClass;
import jetbrains.buildServer.serverSide.priority.PriorityClassManager;
import jetbrains.buildServer.web.openapi.ControllerAction;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.serverSide.priority.controllers.Util.getBuildTypeIds;

/**
 * @author dmitry.neverov
 */
public class DetachBuildTypesAction implements ControllerAction {

  private final PriorityClassManager myPriorityClassManager;
  private final ProjectManager myProjectManager;

  public DetachBuildTypesAction(@NotNull final PriorityClassManager pClassManager,
                                @NotNull final ProjectManager projectManager,
                                @NotNull final PriorityClassActionsController controller) {
    myPriorityClassManager = pClassManager;
    myProjectManager = projectManager;
    controller.registerAction(this);
  }

  public boolean canProcess(@NotNull HttpServletRequest request) {
    return request.getParameter("detachBuildTypes") != null;
  }

  public void process(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response, @Nullable final Element ajaxResponse) {
    String priorityClassId = request.getParameter("pClassId");
    PriorityClass priorityClass = myPriorityClassManager.findPriorityClassById(priorityClassId);
    if (priorityClass != null) {
      Set<String> buildTypesIdsForRemove = getBuildTypeIdsForDetach(request);
      Set<String> updatedBuildTypeIds = getBuildTypeIds(priorityClass);
      boolean buildTypesChanged = updatedBuildTypeIds.removeAll(buildTypesIdsForRemove);

      if (buildTypesChanged) {
        PriorityClass updatedPriorityClass = priorityClass.removeBuildTypes(buildTypesIdsForRemove);
        myPriorityClassManager.savePriorityClass(updatedPriorityClass);

        if (buildTypesIdsForRemove.size() == 1) {
          ActionMessages.getOrCreateMessages(request).addMessage("buildTypesUnassigned", "1 configuration was unassigned from the priority class");
        } else {
          ActionMessages.getOrCreateMessages(request).addMessage("buildTypesUnassigned", "{0} configurations were unassigned from the priority class",
                                                                 String.valueOf(buildTypesIdsForRemove.size()));
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