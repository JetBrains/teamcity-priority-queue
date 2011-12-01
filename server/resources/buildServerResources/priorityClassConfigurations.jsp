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
<%@ include file="/include.jsp" %>

<c:set var="configurationsNum" value="${fn:length(priorityClass.buildTypes)}"/>
<bs:refreshable containerId="pClassBuildTypesContainer" pageUrl="${pageUrl}">
  <a name="configurations"></a><h3 class="title_underlined">Build Configurations</h3>
  <c:choose>
    <c:when test="${priorityClass.personal}">
      <p>This priority class contains all personal builds, they can not be edited</p>
    </c:when>
    <c:otherwise>
      <bs:messages key="buildTypesUnassigned"/>
      <bs:messages key="buildTypesAssigned"/>

      <c:set var="canAddRemoveConfigurations" value="true"/>

      <c:if test="${configurationsNum == 0}">
        <p class="note">There are no configurations added to this priority class.</p>
      </c:if>
      <c:if test="${configurationsNum > 0}">
        <c:url var="action" value="${teamcityPluginResourcesPath}action.html?detachBuildTypes=true"/>
        <form id="unassignBuildTypesForm" action="${action}" style="margin: 0; padding: 0;" onsubmit="return BS.UnassignBuildTypesForm.submit()">
          <p class="note"><strong>${configurationsNum}</strong> configuration<bs:s val="${configurationsNum}"/> added to this priority class.</p>
          <table class="settings priorityClassBuildTypesTable">
            <tr>
              <th class="buildConfigurationName">Build Configuration</th>
              <th class="unassign">
                <forms:checkbox name="selectAll"
                                onmouseover="BS.Tooltip.showMessage(this, {shift: {x: 10, y: 20}, delay: 600}, 'Click to select / unselect all configurations')"
                                onmouseout="BS.Tooltip.hidePopup()"
                                onclick="if (this.checked) BS.UnassignBuildTypesForm.selectAll(true); else BS.UnassignBuildTypesForm.selectAll(false)"/>
              </th>
            </tr>
            <c:forEach items="${sortedBuildTypes}" var="buildType">
              <tr>
                <td>
                  <bs:buildTypeLinkFull buildType="${buildType}"/>
                </td>
                <td class="unassign">
                  <forms:checkbox name="unassign" value="${buildType.id}" />
                </td>
              </tr>
            </c:forEach>
          </table>

          <c:if test="${canAddRemoveConfigurations}">
            <div style="float: right; margin-top: 0.5em;">
              <forms:saving id="unassignInProgress" style="float: none;"/>
              <input type="submit" name="detachBuildTypes" value="Remove from priority class"/>
            </div>

            <input type="hidden" name="pClassId" value="${priorityClass.id}"/>
          </c:if>
        </form>
      </c:if>

      <c:if test="${canAddRemoveConfigurations}">
        <p class="addNew">
          <a href="#" onclick="BS.AttachConfigurationsToClassDialog.showAttachDialog('${priorityClass.id}'); return false">Add configurations</a>
        </p>
        <jsp:include page="${teamcityPluginResourcesPath}attachConfigurationsDialog.html"/>
      </c:if>
    </c:otherwise>
  </c:choose>
</bs:refreshable>
