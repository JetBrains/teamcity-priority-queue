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

<c:set var="title" value="Edit Priority Class ${priorityClass.name}" scope="request"/>

<bs:page>
  <jsp:attribute name="head_include">
    <bs:linkCSS>
      /css/forms.css
      /css/admin/adminMain.css
      ${teamcityPluginResourcesPath}css/priorityClass.css
    </bs:linkCSS>
    <bs:linkScript>
      /js/bs/forms.js
      ${teamcityPluginResourcesPath}js/priorityClass.js
    </bs:linkScript>
    <script type="text/javascript">
      BS.Navigation.items = [
        {title: "Build Queue", url: '<c:url value="/queue.html"/>'},
        {title: "Priority Classes", url: '<c:url value="${teamcityPluginResourcesPath}priorityClassList.html"/>'},
        {title: '<c:out value="${priorityClassBean.priorityClassName}"/>', selected: true}
      ];
    </script>
  </jsp:attribute>

  <jsp:attribute name="body_include">

    <form action="${pageUrl}" id="editPriorityClass" class="priorityClassGeneralSettings" onsubmit="return BS.EditPriorityClassForm.submit();" method="post" autocomplete="off">

      <bs:messages key="priorityClassCreated"/>
      <bs:messages key="priorityClassUpdated"/>

      <table class="runnerFormTable">
        <tr>
          <th><label for="priorityClassName">Name: <l:star/></label></th>
          <td>
            <c:choose>
              <c:when test="${priorityClassBean.personal}">
                <c:out value="${priorityClassBean.priorityClassName}"/>
              </c:when>
              <c:otherwise>
                <forms:textField name="priorityClassName" id="priorityClassName" value="${priorityClassBean.priorityClassName}" style="width:31em"
                                 maxlength="255"/>
                <span class="error" id="error_priorityClassName"></span>
              </c:otherwise>
            </c:choose>
          </td>
        </tr>

        <tr>
          <th><label for="priorityClassPriority">Priority: <l:star/></label></th>
          <td>
            <forms:textField name="priorityClassPriority" id="priorityClassPriority" value="${priorityClassBean.priorityClassPriority}" style="width:5em"
                             maxlength="4"/>&nbsp;<span class="grayNote">Integer in interval [-100..100]</span>
            <span class="error" id="error_priorityClassPriority"></span>
          </td>
        </tr>

        <tr>
          <th><label for="priorityClassDescription">Description:</label></th>
          <td>
            <c:choose>
              <c:when test="${priorityClassBean.personal}">
                <c:out value="${priorityClassBean.priorityClassDescription}"/>
              </c:when>
              <c:otherwise>
                <forms:textField name="priorityClassDescription" value="${priorityClassBean.priorityClassDescription}" style="width:31em"
                                 maxlength="2000"/>
                <span class="error" id="error_priorityClassDescription"></span>
              </c:otherwise>
            </c:choose>
          </td>
        </tr>
      </table>

      <input type="hidden" id="priorityClassId" value="${priorityClassBean.priorityClassId}"/>

      <div class="priorityClassSaveButtonsBlock">
        <forms:cancel cameFromSupport="${priorityClassBean.cameFromSupport}"/>
        <input class="submitButton" type="submit" value="Save"/>
        <forms:saving id="editPriorityClassProgress"/>
      </div>

    </form>

    <%@ include file="priorityClassConfigurations.jsp" %>

  </jsp:attribute>
</bs:page>
