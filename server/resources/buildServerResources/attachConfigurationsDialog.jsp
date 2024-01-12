

<%@include file="/include.jsp"%>
<jsp:useBean id="attachConfigurationsBean"
             type="jetbrains.buildServer.serverSide.priority.controllers.AttachConfigurationsBean" scope="request"/>

<c:url var="actionUrl" value="${teamcityPluginResourcesPath}attachConfigurationsDialog.html"/>
<bs:refreshable containerId="attachConfigurationsToClassContainer" pageUrl="${actionUrl}">
  <bs:modalDialog formId="attachConfigurationsToClass" title="Assign Build Configurations to Priority Class"
                  action="${actionUrl}" closeCommand="BS.AttachConfigurationsToClassDialog.close()"
                  saveCommand="BS.AttachConfigurationsToClassDialog.findConfigurations()">
    <bs:refreshable containerId="configurationListRefreshable" pageUrl="${actionUrl}">
      <div class="actionBar">
        <label class="firstLabel" for="searchString">Find:</label>
        <forms:textField name="searchString" size="20" maxlength="1024" value="${attachConfigurationsBean.searchString}"/>
        <forms:filterButton/>
        <c:if test="${not empty attachConfigurationsBean.searchString}"><forms:resetFilter resetHandler="return BS.AttachConfigurationsToClassDialog.resetFilter();"/></c:if>
        <forms:saving id="findProgress" className="progressRingInline"/>
      </div>

      <c:set var="foundConfigurations" value="${attachConfigurationsBean.foundConfigurations}"/>
      <c:set var="foundConfigurationsNum" value="${fn:length(foundConfigurations)}"/>

      <c:if test="${not attachConfigurationsBean.searchStringSubmitted}">
        <p class="note">Configurations can be found by first letters in their name or project name.</p>
      </c:if>
      <c:if test="${attachConfigurationsBean.searchStringSubmitted}">
        <p class="note">
          Found <strong>${foundConfigurationsNum}</strong> configuration<bs:s val="${foundConfigurationsNum}"/>.
          <c:if test="${attachConfigurationsBean.showFoundConfigurationsNote}">Configurations already included int this class are not shown.</c:if>
        </p>
        <div class="configurationListContainer custom-scroll"><c:if test="${foundConfigurationsNum > 0}"
            ><table class="configurationList">
              <tr>
                <th class="checkbox">
                  <forms:checkbox name="selectAll"
                                  onmouseover="BS.Tooltip.showMessage(this, {shift: {x: 10, y: 20}, delay: 600}, 'Click to select / unselect all configurations')"
                                  onmouseout="BS.Tooltip.hidePopup()"
                                  onclick="if (this.checked) BS.AttachConfigurationsToClassDialog.selectAll(true); else BS.AttachConfigurationsToClassDialog.selectAll(false)"/>
                </th>
                <th class="configurationName">Configuration</th>
                <th class="priorityClass">Priority Class</th>
              </tr>
              <c:forEach items="${foundConfigurations}" var="configuration">
                <tr>
                  <td class="checkbox">
                    <forms:checkbox name="configurationId" value="${configuration.buildType.id}"
                                    onclick="BS.AttachConfigurationsToClassDialog.selectConfiguration(this, ${configuration.priorityClass.defaultPriorityClass})"/>
                  </td>
                  <td><bs:buildTypeLinkFull buildType="${configuration.buildType}"/></td>
                  <td><c:out value="${configuration.priorityClass.name}"/></td>
                </tr>
              </c:forEach>
            </table>
          </c:if
        ></div>

        <input type="hidden" name="pClassId" value="${attachConfigurationsBean.priorityClass.id}"/>
        <input type="hidden" name="submitAction" value=""/>

        <c:if test="${foundConfigurationsNum > 0}">
          <div class="popupSaveButtonsBlock">
            <forms:submit type="button" label="Add to class" onclick="BS.AttachConfigurationsToClassDialog.submit()"/>
            <forms:cancel onclick="BS.AttachConfigurationsToClassDialog.close()"/>
            <forms:saving id="attachProgress"/>
          </div>
        </c:if>
      </c:if>
    </bs:refreshable>
  </bs:modalDialog>
</bs:refreshable>