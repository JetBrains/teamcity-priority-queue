<%--
~ Copyright 2000-2010 JetBrains s.r.o.
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
<c:set var="title" value="Build queue priorities" scope="request"/>

<bs:page>
  <jsp:attribute name="head_include">
    <bs:linkCSS>
      /css/forms.css
      /css/admin/adminMain.css
      ${teamcityPluginResourcesPath}css/priorityClass.css
    </bs:linkCSS>
    <bs:linkScript>
      /js/bs/adminActions.js
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
      <p>Higher priority configuration has - higher place it gets when added to Build Queue.</p>
      <bs:messages key="priorityClassCreated"/>
      <bs:messages key="priorityClassDeleted"/>
      <bs:messages key="priorityClassNotFound"/>
      <bs:messages key="priorityClassUpdated"/>

      <l:tableWithHighlighting highlightImmediately="true" className="priorityClassTable dark sortable borderBottom" mouseovertitle="Click to edit priority class configurations">
        <tr>
          <th class="priorityClassPriority">Priority</th>
          <th class="priorityClassName">Name</th>
          <th class="priorityClassDescription">Description</th>
          <th class="priorityClassConfigurations">Build Configurations</th>
          <th class="actions"></th>
          <th class="actions"></th>
        </tr>

        <c:forEach var="pClass" items="${priorityClasses}" varStatus="pos">
          <c:url value='${teamcityPluginResourcesPath}priorityClassConfigurations.html?priorityClassId=${pClass.id}' var="editUrl"/>

          <c:choose>
            <c:when test="${pClass.default}">
              <c:set var="highlight"></c:set>
              <c:set var="onclick"></c:set>
            </c:when>
            <c:when test="${pClass.personal}">
              <c:set var="highlight"></c:set>
              <c:set var="onclick"></c:set>
            </c:when>
            <c:otherwise>
              <c:set var="highlight">highlight</c:set>
              <c:set var="onclick">onclick="document.location.href='${editUrl}'"</c:set>
            </c:otherwise>
          </c:choose>

          <tr>
            <td class="${highlight}">
              <c:choose>
                <c:when test="${pClass.default}">
                  <c:out value="${pClass.priority}"/>
                </c:when>
                <c:when test="${pClass.personal}">
                  <a href="javascript://" title="Click to edit priority"
                     onclick="BS.EditPriorityClassDialog.showEditDialog('${pClass.id}', 'priorityClassPriority')">
                    <c:out value="${pClass.priority}"/>
                  </a>
                </c:when>
                <c:otherwise>
                  <a href="javascript://" title="Click to edit priority"
                     onclick="BS.EditPriorityClassDialog.showEditDialog('${pClass.id}', 'priorityClassPriority')">
                    <c:out value="${pClass.priority}"/>
                  </a>
                </c:otherwise>
              </c:choose>
            </td>

            <td class="${highlight}">
              <c:choose>
                <c:when test="${pClass.default}">
                  <strong><c:out value="${pClass.name}"/></strong>
                </c:when>
                <c:when test="${pClass.personal}">
                  <strong><c:out value="${pClass.name}"/></strong>
                </c:when>
                <c:otherwise>
                  <strong>                    
                    <a href="javascript://" title="Click to edit name" 
                       onclick="BS.EditPriorityClassDialog.showEditDialog('${pClass.id}', 'priorityClassName')">
                      <c:out value="${pClass.name}"/>
                    </a>
                  </strong>
                </c:otherwise>
              </c:choose>
            </td>

            <td class="${highlight}" ${onclick}>
              <c:out value="${pClass.description}"/>
            </td>

            <td class="${highlight} noTitle">
              <c:choose>
                <c:when test="${pClass.default}">
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
                    <a href="${editUrl}">
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

            <td class="editConfigurations">
              <c:choose>
                <c:when test="${pClass.default}">
                  <span title="This priority class cannot be changed">N/A</span>
                </c:when>
                <c:when test="${pClass.personal}">
                  <a href="javascript://" onclick="BS.EditPriorityClassDialog.showEditDialog('${pClass.id}', 'priorityClassPriority')">edit</a>
                </c:when>
                <c:otherwise>
                  <a href="javascript://" onclick="BS.EditPriorityClassDialog.showEditDialog('${pClass.id}')">edit</a>
                </c:otherwise>
              </c:choose>
            </td>

            <td class="editConfigurations">
              <c:choose>
                <c:when test="${pClass.default}">
                  <span title="This priority class cannot be deleted">N/A</span>
                </c:when>
                <c:when test="${pClass.personal}">
                  <span title="This priority class cannot be deleted">N/A</span>
                </c:when>
                <c:otherwise>
                  <a href="javascript://" onclick="BS.DeletePriorityClassDialog.showDeleteDialog('${pClass.id}', function() {BS.PriorityClassActions.refreshPriorityClassList()})">delete</a>
                </c:otherwise>
              </c:choose>
            </td>
          </tr>
        </c:forEach>
      </l:tableWithHighlighting>

      <p class="addNew"><a href="javascript://" onclick="BS.EditPriorityClassDialog.showCreateDialog()">Create new priority class</a></p>      
    </bs:refreshable>
    
    <jsp:include page="${teamcityPluginResourcesPath}editPriorityClassDialog.html"/>
    <jsp:include page="${teamcityPluginResourcesPath}deletePriorityClassDialog.html"/>
  </jsp:attribute>
</bs:page>
