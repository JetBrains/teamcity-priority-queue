<%--
  ~ Copyright 2000-2012 JetBrains s.r.o.
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
<jsp:useBean id="priorityClass" type="jetbrains.buildServer.serverSide.priority.PriorityClass" scope="request"/>
<jsp:useBean id="buildTypeList" type="java.util.List" scope="request"/>
<c:set var="buildTypesCount" value="${fn:length(priorityClass.buildTypes)}"/>
<div>
  <c:if test="${buildTypesCount == 0}">There are no configurations.</c:if>
  <c:if test="${buildTypesCount == 1}"><strong>1</strong> configuration included into the priority class.</c:if>
  <c:if test="${buildTypesCount > 1}"><strong>${buildTypesCount}</strong> configurations included into the priority class.</c:if>
  <c:if test="${buildTypesCount > fn:length(buildTypeList)}"><br/>Showing no more than <strong>${fn:length(buildTypeList)}</strong> configurations.</c:if>
</div>

<ul class="menuList">
  <c:forEach items="${buildTypeList}" var="buildType">
    <c:set var="menuItem">class="menuItem" onmouseover="this.className = 'menuItemSelected'" onmouseout="this.className = 'menuItem'"</c:set>
    <c:url value="/viewType.html?buildTypeId=${buildType.id}&tab=buildTypeStatusDiv" var="btUrl"/>
    <li self_element="true" ${menuItem} onclick="document.location.href='${btUrl}'">
      <bs:buildTypeLinkFull buildType="${buildType}"/>
    </li>
  </c:forEach>
</ul>
