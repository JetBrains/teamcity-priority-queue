/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.priority.PriorityClass;

/**
 * @author dmitry.neverov
 */
public class AttachConfigurationsBean {
  private PriorityClass myPriorityClass;
  private String mySearchString = "";
  private boolean mySearchStringSubmitted;
  private boolean myShowFoundConfigurationsNote;
  private List<SBuildTypeWithPriority> myFoundConfigurations = new ArrayList<SBuildTypeWithPriority>();
  private List<String> myConfigurationId = new ArrayList<String>();

  public AttachConfigurationsBean() {
  }

  public PriorityClass getPriorityClass() {
    return myPriorityClass;
  }

  public void setPriorityClass(PriorityClass priorityClass) {
    myPriorityClass = priorityClass;
  }

  public String getSearchString() {
    return mySearchString;
  }

  public void setSearchString(String searchString) {
    this.mySearchString = searchString;
  }

  public List<SBuildTypeWithPriority> getFoundConfigurations() {
    return myFoundConfigurations;
  }

  public void setFoundConfigurations(List<SBuildTypeWithPriority> foundConfigurations) {
    myFoundConfigurations = foundConfigurations;
  }

  public boolean isSearchStringSubmitted() {
    return mySearchStringSubmitted;
  }

  public void setSearchStringSubmitted(boolean searchStringSubmitted) {
    mySearchStringSubmitted = searchStringSubmitted;
  }

  public List<String> getConfigurationId() {
    return myConfigurationId;
  }

  public void setConfigurationId(List<String> configurationId) {
    myConfigurationId = configurationId;
  }

  public boolean isShowFoundConfigurationsNote() {
    return myShowFoundConfigurationsNote;
  }

  public void setShowFoundConfigurationsNote(boolean showFoundConfigurationsNote) {
    myShowFoundConfigurationsNote = showFoundConfigurationsNote;
  }

  public static class SBuildTypeWithPriority {
    private final SBuildType myBuildType;
    private final PriorityClass myPriorityClass;

    public SBuildTypeWithPriority(SBuildType buildType, PriorityClass priorityClass) {
      myBuildType = buildType;
      myPriorityClass = priorityClass;
    }

    public SBuildType getBuildType() {
      return myBuildType;
    }

    public PriorityClass getPriorityClass() {
      return myPriorityClass;
    }
  }
}
