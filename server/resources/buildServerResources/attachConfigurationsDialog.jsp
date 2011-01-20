<%--
~ Copyright 2000-2011 JetBrains s.r.o.
~
~ Licensed under the Apache License, Version 2.0 (the "License");
~ you may not use this file except in compliance with the License.
~ You may obtain a copy of the License at
~
~ http://www.apache.org/licenses/LICENSE-2.0
~
~ Unless required by applicable law or agreed to in writing, software
~ distributed under the License is distributed on an "AS IS" BASIS,
~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
~ See the License for the specific language governing permissions and
~ limitations under the License.
--%>

<%@include file="/include.jsp"%>
<jsp:useBean id="attachConfigurationsBean"
             type="jetbrains.buildServer.serverSide.priority.controllers.AttachConfigurationsBean" scope="request"/>

<c:url var="actionUrl" value="${teamcityPluginResourcesPath}attachConfigurationsDialog.html"/>
<bs:refreshable containerId="attachConfigurationsToClassContainer" pageUrl="${actionUrl}">
  <bs:modalDialog formId="attachConfigurationsToClass" title="Assign Build Configurations to Priority Class"
                  action="${actionUrl}" closeCommand="BS.AttachConfigurationsToClassDialog.close()"
                  saveCommand="BS.AttachConfigurationsToClassDialog.findConfigurations()">
    <div class="clr"></div>

    <table class="configurationListFilter">
      <tr>
        <td class="label">
          <label for="searchString" style="width: auto;">Find:</label>
        </td>
        <td class="searchStringField">
          <forms:textField name="searchString" size="20" maxlength="1024" value="${attachConfigurationsBean.searchString}"/>
        </td>
        <td class="button">
          <input type="submit" name="submitFilter" value="Filter"/>
          <forms:saving id="findProgress" style="float: none"/>
        </td>
      </tr>
    </table>

    <bs:refreshable containerId="configurationListRefreshable" pageUrl="${actionUrl}">
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
        <div class="configurationListContainer">
          <c:if test="${foundConfigurationsNum > 0}">
            <table class="configurationList">
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
                                    onclick="BS.AttachConfigurationsToClassDialog.selectConfiguration(this, ${configuration.priorityClass.default})"/>
                  </td>
                  <td><bs:buildTypeLinkFull buildType="${configuration.buildType}"/></td>
                  <td><c:out value="${configuration.priorityClass.name}"/></td>
                </tr>
              </c:forEach>
            </table>
          </c:if>
        </div>

        <input type="hidden" name="pClassId" value="${attachConfigurationsBean.priorityClass.id}"/>
        <input type="hidden" name="submitAction" value=""/>

        <c:if test="${foundConfigurationsNum > 0}">
          <div class="popupSaveButtonsBlock">
            <a href="javascript://" onclick="BS.AttachConfigurationsToClassDialog.close()" class="cancel">Cancel</a>
            <input class="submitButton" type="button" value="Add to class" onclick="BS.AttachConfigurationsToClassDialog.submit()"/>
            <forms:saving id="attachProgress"/>
          </div>
        </c:if>

      </c:if>
    </bs:refreshable>

    <br clear="all"/>
  </bs:modalDialog>
</bs:refreshable>
