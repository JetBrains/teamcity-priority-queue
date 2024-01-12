

package jetbrains.buildServer.serverSide.priority;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.ServerPaths;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;

/**
 * @author dmitry.neverov
 */
final class Util {

  private static int BUILD_TYPE_SEQUENCE = 0;

  static SBuildType createBuildType(Mockery context, final String buildTypeId) {
    return createBuildType(context, buildTypeId, buildTypeId);
  }

  static SBuildType createBuildType(Mockery context, final String buildTypeId, final String externalBuildTypeId) {
    final SBuildType bt = context.mock(SBuildType.class, "bt" + BUILD_TYPE_SEQUENCE++);
    context.checking(new Expectations() {{
      allowing(bt).getBuildTypeId(); will(returnValue(buildTypeId));
      allowing(bt).getExternalId(); will(returnValue(externalBuildTypeId));
    }});
    return bt;
  }

  static Map<String, SBuildType> prepareBuildTypes(Mockery context, final ProjectManager projectManager, @NotNull String... btIds) {
    Map<String, String> internal2externalId = new HashMap<String, String>();
    for (String btId : btIds) {
      internal2externalId.put(btId, btId);
    }
    return prepareBuildTypes(context, projectManager, internal2externalId);
  }

  static Map<String, SBuildType> prepareBuildTypes(Mockery context, final ProjectManager projectManager, @NotNull final Map<String, String> btIds) {
    final Map<String, SBuildType> id2buildType = new HashMap<String, SBuildType>();
    for (Map.Entry<String, String> e : btIds.entrySet()) {
      String btId = e.getKey();
      String externalId = e.getValue();
      SBuildType buildType = createBuildType(context, btId, externalId);
      id2buildType.put(btId, buildType);
    }
    context.checking(new Expectations() {{
      for (Map.Entry<String, SBuildType> btEntry : id2buildType.entrySet()) {
        allowing(projectManager).findBuildTypeById(btEntry.getKey()); will(returnValue(btEntry.getValue()));
        allowing(projectManager).findBuildTypeByExternalId(btIds.get(btEntry.getKey())); will(returnValue(btEntry.getValue()));
      }
      allowing(projectManager).getAllBuildTypes(); will(returnValue(new ArrayList<SBuildType>(id2buildType.values())));
    }});
    return id2buildType;
  }

  static File getTestDataDir() {
    File f = new File("external-repos/priority-queue/server/testData");
    if (f.isDirectory()) {
      return f;
    }
    return new File("server/testData");
  }

  static ServerPaths getServerPaths(File rootDir) throws IOException {
    File systemDir = new File(rootDir, "system");
    File backupDir = new File(rootDir, "backup");
    File importDir = new File(rootDir, "import");
    return new ServerPaths(systemDir.getAbsolutePath(), getTestDataDir().getAbsolutePath(), backupDir.getAbsolutePath(), importDir.getAbsolutePath());
  }

}