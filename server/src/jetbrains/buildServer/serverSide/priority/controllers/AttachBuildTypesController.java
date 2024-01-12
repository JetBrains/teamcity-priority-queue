

package jetbrains.buildServer.serverSide.priority.controllers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.ActionMessages;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.FormUtil;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.priority.PriorityClass;
import jetbrains.buildServer.serverSide.priority.PriorityClassManager;
import jetbrains.buildServer.serverSide.priority.exceptions.PriorityClassException;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

import static jetbrains.buildServer.serverSide.priority.controllers.Util.getBuildTypeIds;

/**
 * @author dmitry.neverov
 */
public class AttachBuildTypesController extends BaseFormXmlController {

  private final PriorityClassManager myPriorityClassManager;
  private final PluginDescriptor myPluginDescriptor;
  private final ProjectManager myProjectManager;

  public AttachBuildTypesController(@NotNull final SBuildServer server,
                                    @NotNull final PriorityClassManager pClassManager,
                                    @NotNull final ProjectManager projectManager,
                                    @NotNull final WebControllerManager controllerManager,
                                    @NotNull final PluginDescriptor pluginDescriptor) {
    super(server);
    myPriorityClassManager = pClassManager;
    myProjectManager = projectManager;
    myPluginDescriptor = pluginDescriptor;
    controllerManager.registerController(myPluginDescriptor.getPluginResourcesPath() + "attachConfigurationsDialog.html", this);
  }

  @Override
  protected ModelAndView doGet(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response) {
    AttachConfigurationsBean bean = getBean(request);

    if (bean.isSearchStringSubmitted()) {
      PriorityClass priorityClass = bean.getPriorityClass();
      bean.setFoundConfigurations(findConfigurations(priorityClass, bean.getSearchString().trim()));
    }

    ModelAndView mv = new ModelAndView(myPluginDescriptor.getPluginResourcesPath("attachConfigurationsDialog.jsp"));
    mv.getModel().put("attachConfigurationsBean", bean);
    return mv;
  }

  @Override
  protected void doPost(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response, @NotNull final Element xmlResponse) {
    AttachConfigurationsBean bean = getBean(request);
    bindFromRequest(request, bean);

    if (isAttachConfigurationsRequest(request)) {
      List<String> selectedConfigurations = bean.getConfigurationId();

      ActionErrors errors = new ActionErrors();
      try {
        if (selectedConfigurations != null && !selectedConfigurations.isEmpty()) {
          Set<String> newBuildTypeIds = new HashSet<String>(selectedConfigurations);
          PriorityClass oldPriorityClass = bean.getPriorityClass();
          Set<String> oldBuildTypeIds = getBuildTypeIds(oldPriorityClass);
          newBuildTypeIds.addAll(oldBuildTypeIds);

          PriorityClass updatedPriorityClass = oldPriorityClass.addBuildTypes(selectedConfigurations);
          myPriorityClassManager.savePriorityClass(updatedPriorityClass);

          ActionMessages messages = ActionMessages.getOrCreateMessages(request);
          int addedCount = newBuildTypeIds.size() - oldBuildTypeIds.size();
          if (addedCount == 1) {
            messages.addMessage("buildTypesAssigned", "1 configuration was successfully assigned to the priority class");
          } else {
            messages.addMessage("buildTypesAssigned", "{0} configurations were successfully assigned to the priority class", String.valueOf(addedCount));
          }
        }
      } catch (PriorityClassException e) {
        errors.addError("attachToClass", e.getMessage());
      }
    }
  }

  private List<AttachConfigurationsBean.SBuildTypeWithPriority> findConfigurations(final PriorityClass priorityClass, @NotNull String searchString) {
    List<AttachConfigurationsBean.SBuildTypeWithPriority> result = new ArrayList<AttachConfigurationsBean.SBuildTypeWithPriority>();
    final Pattern pattern = Pattern.compile(".*" + Pattern.quote(searchString) + ".*", Pattern.CASE_INSENSITIVE);
    List<SBuildType> buildTypes = myProjectManager.getActiveBuildTypes();
    buildTypes.removeAll(priorityClass.getBuildTypes());
    for (SBuildType bt : buildTypes) {
      if (pattern.matcher(bt.getFullName()).matches()) {
        PriorityClass pc = myPriorityClassManager.getBuildTypePriorityClass(bt);
        result.add(new AttachConfigurationsBean.SBuildTypeWithPriority(bt, pc));
      }
    }
    return result;
  }

  private boolean isAttachConfigurationsRequest(final HttpServletRequest request) {
    return "assignConfigurations".equals(request.getParameter("submitAction"));
  }

  private AttachConfigurationsBean getBean(final HttpServletRequest request) {
    AttachConfigurationsBean bean = FormUtil.getOrCreateForm(request, AttachConfigurationsBean.class,
            new FormUtil.FormCreator<AttachConfigurationsBean>() {
              public AttachConfigurationsBean createForm(final HttpServletRequest request) {
                return new AttachConfigurationsBean();
              }
            });

    assert bean != null;

    String pClassId = request.getParameter("pClassId");
    if (pClassId != null) {
      bean.setPriorityClass(myPriorityClassManager.findPriorityClassById(pClassId));
    }
    if ("true".equals(request.getParameter("openDialog"))) {
      //on dialog open search for configurations only if previous search string was not empty
      //otherwise dialog could be opened too slow
      bean.setSearchStringSubmitted(!StringUtil.isEmptyOrSpaces(bean.getSearchString()));
    } else {
      bean.setSearchString(request.getParameter("searchString"));
      bean.setSearchStringSubmitted("true".equals(request.getParameter("searchStringSubmitted")));
    }
    return bean;
  }
}