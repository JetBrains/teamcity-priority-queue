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

import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.ActionMessages;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.FormUtil;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.priority.PriorityClass;
import jetbrains.buildServer.serverSide.priority.PriorityClassImpl;
import jetbrains.buildServer.serverSide.priority.PriorityClassManager;
import jetbrains.buildServer.serverSide.priority.exceptions.PriorityClassException;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

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
  protected ModelAndView doGet(final HttpServletRequest request, final HttpServletResponse response) {
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
  protected void doPost(final HttpServletRequest request, final HttpServletResponse response, final Element xmlResponse) {
    AttachConfigurationsBean bean = getBean(request);
    bindFromRequest(request, bean);

    if (isAttachConfigurationsRequest(request)) {
      List<String> selectedConfigurations = bean.getConfigurationId();

      ActionErrors errors = new ActionErrors();
      try {
        if (selectedConfigurations != null && !selectedConfigurations.isEmpty()) {
          List<SBuildType> newBuildTypes = new ArrayList<SBuildType>();
          for (String btId: selectedConfigurations) {
            SBuildType configuration = myServer.getProjectManager().findBuildTypeById(btId);
            if (configuration != null) {
              newBuildTypes.add(configuration);
            }
          }
          PriorityClass oldPriorityClass = bean.getPriorityClass();
          newBuildTypes.addAll(oldPriorityClass.getBuildTypes());
          PriorityClassImpl updatedPriorityClass = new PriorityClassImpl(oldPriorityClass.getId(), oldPriorityClass.getName(),
                  oldPriorityClass.getDescription(), oldPriorityClass.getPriority(), new HashSet<SBuildType>(newBuildTypes));
          myPriorityClassManager.savePriorityClass(updatedPriorityClass);

          ActionMessages messages = ActionMessages.getOrCreateMessages(request);
          if (newBuildTypes.size() == 1) {
            messages.addMessage("buildTypesAssigned", "1 configuration was successfully assigned to the priority class");
          } else {
            messages.addMessage("buildTypesAssigned", "{0} configuration were successfully assigned to the priority class", String.valueOf(newBuildTypes.size()));
          }
        }
      } catch (PriorityClassException e) {
        errors.addError("attachToClass", e.getMessage());
      }
    }
  }

  private List<AttachConfigurationsBean.SBuildTypeWithPriority> findConfigurations(final PriorityClass priorityClass, final String searchString) {
    List<AttachConfigurationsBean.SBuildTypeWithPriority> result = new ArrayList<AttachConfigurationsBean.SBuildTypeWithPriority>();
    final Pattern pattern = Pattern.compile(".*\\b" + searchString + ".*", Pattern.CASE_INSENSITIVE); 
    List<SBuildType> buildTypes = myProjectManager.getAllBuildTypes();
    buildTypes.removeAll(priorityClass.getBuildTypes());
    for (SBuildType bt : buildTypes) {
      if (pattern.matcher(bt.getProjectName() + " " + bt.getName()).matches()) {
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
    return bean;
  }
}
