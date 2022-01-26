<%--
  ~ Copyright 2000-2022 JetBrains s.r.o.
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

<jsp:useBean id="pageUrl" type="java.lang.String" scope="request"/>
<c:set var="pageTitle" value="Build queue priorities" scope="request"/>

<bs:page>
  <jsp:attribute name="head_include">
    <bs:linkCSS>
      /css/admin/adminMain.css
      ${teamcityPluginResourcesPath}css/priorityClass.css
    </bs:linkCSS>
    <bs:linkScript>
      /js/bs/queueLikeSorter.js
    </bs:linkScript>
    <script type="text/javascript">
      BS.Navigation.items = [
        {title: "Build Queue", url: '<c:url value="/queue.html"/>'},
        {title: "Priority Classes", selected: true}
      ];
    </script>
    <bs:linkScript>
      ${teamcityPluginResourcesPath}js/priorityClass.js
      /js/bs/blocks.js
      /js/bs/blocksWithHeader.js
    </bs:linkScript>
  </jsp:attribute>

  <jsp:attribute name="body_include">
    <bs:refreshable containerId="priorityClassList" pageUrl="${pageUrl}">
      <p>
        The higher priority a configuration has - the higher place it gets when added to
        the Build Queue<bs:help file="Ordering+Build+Queue#OrderingBuildQueue-ManagingBuildPriorities"/>
      </p>
      <bs:messages key="priorityClassCreated"/>
      <bs:messages key="priorityClassDeleted"/>
      <bs:messages key="priorityClassNotFound"/>
      <bs:messages key="priorityClassUpdated"/>

      <l:tableWithHighlighting highlightImmediately="true" className="settings priorityClassTable">
        <tr>
          <th class="name priorityClassPriority">Priority</th>
          <th class="name priorityClassName">Name</th>
          <th class="name priorityClassDescription">Description</th>
          <th class="name priorityClassConfigurations" colspan="3">Build Configurations</th>
        </tr>

        <c:forEach var="pClass" items="${priorityClasses}" varStatus="pos">
          <c:url var="editUrl" value='${teamcityPluginResourcesPath}editPriorityClass.html?priorityClassId=${pClass.id}'/>

          <c:choose>
            <c:when test="${pClass.defaultPriorityClass}">
              <c:set var="highlight"></c:set>
              <c:set var="onclick"></c:set>
            </c:when>
            <c:otherwise>
              <c:set var="highlight">highlight</c:set>
              <c:set var="onclick">onclick="document.location.href='${editUrl}'"</c:set>
            </c:otherwise>
          </c:choose>

          <tr>
            <td class="${highlight}" ${onclick}>
              <c:out value="${pClass.priority}"/>
            </td>

            <td class="${highlight}" ${onclick}>
              <strong><c:out value="${pClass.name}"/></strong>
            </td>

            <td class="${highlight}" ${onclick}>
              <c:out value="${pClass.description}"/>
            </td>

            <td class="${highlight}" ${onclick}>
              <c:choose>
                <c:when test="${pClass.defaultPriorityClass}">
                  <span title="This class contains all build configurations not included into any other priority classes, they cannot be edited">N/A</span>
                </c:when>
                <c:when test="${pClass.personal}">
                  <span title="This class contains all personal builds, they cannot be edited">N/A</span>
                </c:when>
                <c:otherwise>
                  <c:set var="buildTypeCount" value="${fn:length(pClass.buildTypes)}"/>
                  <bs:popupControl showPopupCommand="BS.PriorityClassConfigurationsPopup.showPopup(this, '${pClass.id}')"
                                   hidePopupCommand="BS.PriorityClassConfigurationsPopup.hidePopup()"
                                   stopHidingPopupCommand="BS.PriorityClassConfigurationsPopup.stopHidingPopup()"
                                   controlId="priorityClasses:${pClass.id}">
                    <a href="${editUrl}#configurations">
                      <c:choose>
                        <c:when test="${buildTypeCount > 0}">
                          View configurations (${buildTypeCount})
                        </c:when>
                        <c:otherwise>
                          No configurations
                        </c:otherwise>
                      </c:choose>
                    </a>
                  </bs:popupControl>
                </c:otherwise>
              </c:choose>
            </td>

            <td class="${highlight}">
              <c:choose>
                <c:when test="${pClass.defaultPriorityClass}">
                  <span title="This priority class cannot be changed">N/A</span>
                </c:when>
                <c:otherwise>
                  <a href="${editUrl}">Edit</a>
                </c:otherwise>
              </c:choose>
            </td>

            <td class="editConfigurations">
              <c:choose>
                <c:when test="${pClass.defaultPriorityClass}">
                  <span title="This priority class cannot be deleted">N/A</span>
                </c:when>
                <c:when test="${pClass.personal}">
                  <span title="This priority class cannot be deleted">N/A</span>
                </c:when>
                <c:otherwise>
                  <a href="#" onclick="BS.DeletePriorityClassDialog.showDeleteDialog('${pClass.id}', function() {BS.PriorityClassActions.refreshPriorityClassList()}); return false">Delete</a>
                </c:otherwise>
              </c:choose>
            </td>
          </tr>
        </c:forEach>
      </l:tableWithHighlighting>

      <c:url value='${teamcityPluginResourcesPath}createPriorityClass.html' var="addClassUrl"/>
      <p><forms:addButton href="${addClassUrl}">Create new priority class</forms:addButton></p>
    </bs:refreshable>
    
    <jsp:include page="${teamcityPluginResourcesPath}deletePriorityClassDialog.html"/>
  </jsp:attribute>
</bs:page>
