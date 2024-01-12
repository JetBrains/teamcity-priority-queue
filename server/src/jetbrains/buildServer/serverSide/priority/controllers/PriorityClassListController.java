

package jetbrains.buildServer.serverSide.priority.controllers;

import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.priority.PriorityClass;
import jetbrains.buildServer.serverSide.priority.PriorityClassManager;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author dmitry.neverov
 */
public class PriorityClassListController extends BaseController {

  private final PluginDescriptor myPluginDescriptor;
  private final PriorityClassManager myPriorityClassManager;

  public PriorityClassListController(@NotNull final PluginDescriptor pluginDescriptor,
                                     @NotNull final WebControllerManager manager,
                                     @NotNull final PriorityClassManager pClassManager) {
    myPluginDescriptor = pluginDescriptor;
    myPriorityClassManager = pClassManager;
    manager.registerController(myPluginDescriptor.getPluginResourcesPath() + "priorityClassList.html", this);
  }

  @Override
  protected ModelAndView doHandle(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response) throws Exception {
    ModelAndView view = new ModelAndView(myPluginDescriptor.getPluginResourcesPath("priorityClassList.jsp"));
    final Map model = view.getModel();
    model.put("priorityClasses", getPriorityClasses());
    return view;
  }

  private Collection<PriorityClass> getPriorityClasses() {
    List<PriorityClass> pClasses = myPriorityClassManager.getAllPriorityClasses();
    Collections.sort(pClasses, new PriorityClassComparator());
    return pClasses;
  }

  private static class PriorityClassComparator implements Comparator<PriorityClass> {
    public int compare(PriorityClass o1, PriorityClass o2) {
      if (o1.getPriority() > o2.getPriority()) return -1;
      if (o1.getPriority() < o2.getPriority()) return 1;
      return o1.getName().compareTo(o2.getName());      
    }
  }
  
}