package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
import com.intellij.openapi.vfs.VirtualFile;
import static org.easymock.classextension.EasyMock.*;
import org.junit.Before;
import org.junit.Test;

public class FileListenerTest extends FileListenerTestCase {
  private TestFileFilter filter;

  @Before
  public void setUp() {
    filter = new TestFileFilter();
    l = new FileListener(vcs, fileSystem, filter);
  }

  @Test
  public void testCreatingFiles() {
    VirtualFile f = new TestVirtualFile("file", "content", 123L);
    fireCreated(f);

    Entry e = vcs.findEntry("file");
    assertNotNull(e);

    assertFalse(e.isDirectory());

    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  public void testTakingPhysicalFileContentOnCreation() {
    configureLocalFileSystemToReturnPhysicalContent("physical");

    VirtualFile f = new TestVirtualFile("f", "memory", null);
    fireCreated(f);

    assertEquals(c("physical"), vcs.getEntry("f").getContent());
  }

  @Test
  public void testCreatingDirectories() {
    VirtualFile f = new TestVirtualFile("dir", 345L);
    fireCreated(f);

    Entry e = vcs.findEntry("dir");
    assertNotNull(e);

    assertTrue(e.isDirectory());
    assertEquals(345L, e.getTimestamp());
  }

  @Test
  public void testCreatingDirectoriesWithChildren() {
    TestVirtualFile dir1 = new TestVirtualFile("dir1", null);
    TestVirtualFile dir2 = new TestVirtualFile("dir2", null);
    TestVirtualFile file = new TestVirtualFile("file", "", null);

    dir1.addChild(dir2);
    dir2.addChild(file);
    fireCreated(dir1);

    assertTrue(vcs.hasEntry("dir1"));
    assertTrue(vcs.hasEntry("dir1/dir2"));
    assertTrue(vcs.hasEntry("dir1/dir2/file"));
  }

  @Test
  public void testCreationOfDirectoryWithChildrenIsThreatedAsOneChange() {
    TestVirtualFile dir = new TestVirtualFile("dir", null);
    dir.addChild(new TestVirtualFile("one", null, null));
    dir.addChild(new TestVirtualFile("two", null, null));
    fireCreated(dir);

    assertTrue(vcs.hasEntry("dir"));
    assertTrue(vcs.hasEntry("dir/one"));
    assertTrue(vcs.hasEntry("dir/two"));

    assertEquals(1, vcs.getLabelsFor("dir").size());
  }

  @Test
  public void testChangingFileContent() {
    vcs.createFile("file", b("old content"), null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("file", "new content", 505L);
    fireContentChanged(f);

    Entry e = vcs.getEntry("file");
    assertEquals(c("new content"), e.getContent());
    assertEquals(505L, e.getTimestamp());
  }

  @Test
  public void testTakingPhysicalFileContentOnContentChange() {
    configureLocalFileSystemToReturnPhysicalContent("physical");

    vcs.createFile("f", b("content"), null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("f", "memory", null);
    fireContentChanged(f);

    assertEquals(c("physical"), vcs.getEntry("f").getContent());
  }


  @Test
  public void testRenaming() {
    vcs.createFile("old name", b("old content"), null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("new name", null, null);
    fireRenamed(f, "old name");

    assertFalse(vcs.hasEntry("old name"));

    Entry e = vcs.findEntry("new name");
    assertNotNull(e);

    assertEquals(c("old content"), e.getContent());
  }

  @Test
  public void testDoNothingOnAnotherPropertyChanges() throws Exception {
    // we just shouldn't throw any exception here to meake test pass
    VirtualFile f = new TestVirtualFile(null, null, null);
    firePropertyChanged(f, "another property", null);
  }


  @Test
  public void testMoving() {
    vcs.createDirectory("dir1", null);
    vcs.createDirectory("dir2", null);
    vcs.createFile("dir1/file", b("content"), null);
    vcs.apply();

    TestVirtualFile oldParent = new TestVirtualFile("dir1", null);
    TestVirtualFile newParent = new TestVirtualFile("dir2", null);
    TestVirtualFile f = new TestVirtualFile("file", null, null);
    newParent.addChild(f);
    fireMoved(f, oldParent, newParent);

    assertFalse(vcs.hasEntry("dir1/file"));

    Entry e = vcs.findEntry("dir2/file");

    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
  }

  @Test
  public void testMovingFilteredFile() {
    vcs.createDirectory("dir1", null);
    vcs.createDirectory("dir2", null);
    vcs.apply();

    TestVirtualFile oldParent = new TestVirtualFile("dir1", null);
    TestVirtualFile newParent = new TestVirtualFile("dir2", null);
    TestVirtualFile f = new TestVirtualFile("file", null, null);
    newParent.addChild(f);

    filter.setNotAllowedFiles(f);

    fireMoved(f, oldParent, newParent);
    assertFalse(vcs.hasEntry("dir1/file"));
  }

  @Test
  public void testMovingFromOutsideOfTheContentRoots() {
    vcs.createDirectory("myRoot", null);
    vcs.apply();

    TestVirtualFile f = new TestVirtualFile("file", "content", null);
    TestVirtualFile oldParent = new TestVirtualFile("anotherRoot", null);
    TestVirtualFile newParent = new TestVirtualFile("myRoot", null);
    newParent.addChild(f);

    filter.setFilesNotUnderContentRoot(oldParent);

    fireMoved(f, oldParent, newParent);

    Entry e = vcs.findEntry("myRoot/file");
    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
  }

  @Test
  public void testMovingFilteredFileFromOutsideOfTheContentRoots() {
    vcs.createDirectory("myRoot", null);
    vcs.apply();

    TestVirtualFile f = new TestVirtualFile("file", "content", null);
    TestVirtualFile oldParent = new TestVirtualFile("anotherRoot", null);
    TestVirtualFile newParent = new TestVirtualFile("myRoot", null);
    newParent.addChild(f);

    filter.setFilesNotUnderContentRoot(oldParent);
    filter.setNotAllowedFiles(f);

    fireMoved(f, oldParent, newParent);

    assertFalse(vcs.hasEntry("myRoot/file"));
  }

  @Test
  public void testMovingToOutsideOfTheContentRoots() {
    vcs.createDirectory("myRoot", null);
    vcs.createFile("myRoot/file", null, null);
    vcs.apply();

    TestVirtualFile f = new TestVirtualFile("file", "content", null);
    TestVirtualFile oldParent = new TestVirtualFile("myRoot", null);
    TestVirtualFile newParent = new TestVirtualFile("anotherRoot", null);
    newParent.addChild(f);

    filter.setFilesNotUnderContentRoot(newParent);

    fireMoved(f, oldParent, newParent);

    assertFalse(vcs.hasEntry("myRoot/file"));
    assertFalse(vcs.hasEntry("anotherRoot/file"));
  }

  @Test
  public void testMovingFilteredFileToOutsideOfTheContentRoots() {
    vcs.createDirectory("myRoot", null);
    vcs.apply();

    TestVirtualFile f = new TestVirtualFile("file", "content", null);
    TestVirtualFile oldParent = new TestVirtualFile("myRoot", null);
    TestVirtualFile newParent = new TestVirtualFile("anotherRoot", null);
    newParent.addChild(f);

    filter.setFilesNotUnderContentRoot(newParent);
    filter.setNotAllowedFiles(f);

    fireMoved(f, oldParent, newParent);
    assertFalse(vcs.hasEntry("myRoot/file"));
  }

  @Test
  public void testMovingAroundOutsideContentRoots() {
    TestVirtualFile f = new TestVirtualFile("file", "content", null);
    TestVirtualFile oldParent = new TestVirtualFile("root1", null);
    TestVirtualFile newParent = new TestVirtualFile("root2", null);
    newParent.addChild(f);

    filter.setFilesNotUnderContentRoot(oldParent, newParent);

    fireMoved(f, oldParent, newParent);

    assertFalse(vcs.hasEntry("root1/file"));
    assertFalse(vcs.hasEntry("root2/file"));
  }

  @Test
  public void testDeletionFromDirectory() {
    vcs.createDirectory("dir", null);
    vcs.createFile("file", null, null);
    vcs.apply();

    VirtualFile dir = new TestVirtualFile("dir", null, null);
    VirtualFile f = new TestVirtualFile("file", null, null);
    fireDeleted(f, dir);

    assertTrue(vcs.hasEntry("dir"));
    assertFalse(vcs.hasEntry("dir/file"));
  }

  @Test
  public void testDeletionWithoutParent() {
    vcs.createFile("file", null, null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("file", null, null);
    fireDeleted(f, null);

    assertFalse(vcs.hasEntry("file"));
  }

  @Test
  public void testDeletionOfFileThanIsNotUnderVcsDoesNotThrowException() {
    VirtualFile f = new TestVirtualFile("non-existent", null, null);
    fireDeleted(f, null); // should'n throw
  }

  @Test
  public void testFilteringFiles() {
    vcs = createMock(LocalVcs.class);
    replay(vcs);

    setUp();

    VirtualFile f = new TestVirtualFile("file", null, null);
    filter.setFilesNotUnderContentRoot(f);

    fireCreated(f);
    fireContentChanged(f);
    fireMoved(f, f, f);

    verify(vcs);
  }
}
