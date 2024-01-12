

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