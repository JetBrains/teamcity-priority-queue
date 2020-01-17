/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.serverSide.priority;

import jetbrains.buildServer.serverSide.SBuildType;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.jetbrains.annotations.NotNull;

class BuildTypeMatcher extends TypeSafeMatcher<SBuildType> {

  private String myBuildTypeId;

  public static BuildTypeMatcher buildType() {
    return new BuildTypeMatcher();
  }

  public BuildTypeMatcher withId(@NotNull String buildTypeId) {
    myBuildTypeId = buildTypeId;
    return this;
  }

  @Override
  public boolean matchesSafely(final SBuildType item) {
    return myBuildTypeId == null || myBuildTypeId.equals(item.getBuildTypeId());
  }

  public void describeTo(final Description description) {
    description.appendText("buildType with ");
    if (myBuildTypeId != null)
      description.appendText(" id ").appendValue(myBuildTypeId);
  }
}
