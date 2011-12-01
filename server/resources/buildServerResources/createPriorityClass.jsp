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

<c:set var="title" value="Create New Priority Class" scope="request"/>

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
        {title: "New Priority Class", selected: true}
      ];
    </script>
  </jsp:attribute>

  <jsp:attribute name="body_include">

    <form action="${pageUrl}" id="createPriorityClass" class="priorityClassGeneralSettings" onsubmit="return BS.CreatePriorityClassForm.submit();" method="post" autocomplete="off">

      <table class="runnerFormTable">
        <tr>
          <th><label for="priorityClassName">Name: <l:star/></label></th>
          <td>
            <forms:textField name="priorityClassName" id="priorityClassName" value="${priorityClassBean.priorityClassName}" className="longField"
                             maxlength="255"/>
            <span class="error" id="error_priorityClassName"></span>
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
            <forms:textField name="priorityClassDescription" value="${priorityClassBean.priorityClassDescription}" className="longField"
                             maxlength="2000"/>
            <span class="error" id="error_priorityClassDescription"></span>
          </td>
        </tr>
      </table>

      <input type="hidden" id="afterCreateLocation" value="<c:url value='${teamcityPluginResourcesPath}editPriorityClass.html'/>"/>

      <div class="priorityClassSaveButtonsBlock">
        <forms:cancel cameFromSupport="${priorityClassBean.cameFromSupport}"/>
        <forms:submit label="Create"/>
        <forms:saving id="createPriorityClassProgress"/>
      </div>

    </form>

  </jsp:attribute>
</bs:page>
