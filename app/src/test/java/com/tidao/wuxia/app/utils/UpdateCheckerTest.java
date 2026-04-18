package com.tidao.wuxia.app.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * VersionUtils 版本号解析与比较逻辑的单元测试。
 * 测试对象均为包私有静态方法，运行在本地 JVM（不依赖 Android 运行时）。
 */
public class UpdateCheckerTest {

    // ========== isNewerVersion ==========

    @Test
    public void isNewerVersion_majorIncrement_returnsTrue() {
        assertTrue(VersionUtils.isNewerVersion("2.0.0", "1.9.9"));
    }

    @Test
    public void isNewerVersion_minorIncrement_returnsTrue() {
        assertTrue(VersionUtils.isNewerVersion("1.3.0", "1.2.1"));
    }

    @Test
    public void isNewerVersion_patchIncrement_returnsTrue() {
        assertTrue(VersionUtils.isNewerVersion("1.2.2", "1.2.1"));
    }

    @Test
    public void isNewerVersion_sameVersion_returnsFalse() {
        assertFalse(VersionUtils.isNewerVersion("1.2.1", "1.2.1"));
    }

    @Test
    public void isNewerVersion_olderVersion_returnsFalse() {
        assertFalse(VersionUtils.isNewerVersion("1.0.0", "1.2.1"));
    }

    @Test
    public void isNewerVersion_shortVsLongSegments_paddedCorrectly() {
        // "2" vs "1.9.9" → 2.0.0 > 1.9.9
        assertTrue(VersionUtils.isNewerVersion("2", "1.9.9"));
        // "1.3" vs "1.2.1" → 1.3.0 > 1.2.1
        assertTrue(VersionUtils.isNewerVersion("1.3", "1.2.1"));
    }

    @Test
    public void isNewerVersion_prereleaseBaseSuffix_ignoresSuffix() {
        // "1.3.0-alpha" 基础版本 > "1.2.1"
        assertTrue(VersionUtils.isNewerVersion("1.3.0-alpha", "1.2.1"));
        // "1.2.1-alpha" 与 "1.2.1" 基础相同，不算更新
        assertFalse(VersionUtils.isNewerVersion("1.2.1-alpha", "1.2.1"));
    }

    @Test
    public void isNewerVersion_invalidInput_returnsFalse() {
        assertFalse(VersionUtils.isNewerVersion("invalid", "1.0.0"));
        assertFalse(VersionUtils.isNewerVersion("1.0.0", "invalid"));
        assertFalse(VersionUtils.isNewerVersion("", "1.0.0"));
        assertFalse(VersionUtils.isNewerVersion("1.0.0", ""));
    }

    // ========== isSameVersion ==========

    @Test
    public void isSameVersion_identical_returnsTrue() {
        assertTrue(VersionUtils.isSameVersion("1.2.1", "1.2.1"));
    }

    @Test
    public void isSameVersion_differentPatch_returnsFalse() {
        assertFalse(VersionUtils.isSameVersion("1.2.2", "1.2.1"));
    }

    @Test
    public void isSameVersion_prereleaseVsBase_sameBase_returnsTrue() {
        // 忽略 "-" 之后的后缀，基础版本相同视为相同
        assertTrue(VersionUtils.isSameVersion("1.2.1-alpha", "1.2.1"));
    }

    @Test
    public void isSameVersion_paddedZeroVsExplicit_returnsTrue() {
        // "1.2" == "1.2.0"（补零后相同）
        assertTrue(VersionUtils.isSameVersion("1.2", "1.2.0"));
    }

    // ========== extractVersionFromName ==========

    @Test
    public void extractVersionFromName_standardCiFormat_extractsVersion() {
        assertEquals("1.2.3", VersionUtils.extractVersionFromName("最新版本 v1.2.3"));
    }

    @Test
    public void extractVersionFromName_multiSegmentVersion_extractsAll() {
        assertEquals("1.2.3", VersionUtils.extractVersionFromName("Release v1.2.3 Build 42"));
    }

    @Test
    public void extractVersionFromName_twoSegmentVersion_extractsCorrectly() {
        assertEquals("1.3", VersionUtils.extractVersionFromName("最新版本 v1.3"));
    }

    @Test
    public void extractVersionFromName_noVersionPattern_returnsNull() {
        assertNull(VersionUtils.extractVersionFromName("Latest Release"));
    }

    @Test
    public void extractVersionFromName_emptyString_returnsNull() {
        assertNull(VersionUtils.extractVersionFromName(""));
    }

    @Test
    public void extractVersionFromName_nullInput_returnsNull() {
        assertNull(VersionUtils.extractVersionFromName(null));
    }

    // ========== extractBuildNumber ==========

    @Test
    public void extractBuildNumber_standardFormat_extractsNumber() {
        Integer result = VersionUtils.extractBuildNumber("最新版本 v1.2.3 Build 42");
        assertNotNull(result);
        assertEquals(42, result.intValue());
    }

    @Test
    public void extractBuildNumber_buildAtStart_extractsNumber() {
        Integer result = VersionUtils.extractBuildNumber("Build 100 some text");
        assertNotNull(result);
        assertEquals(100, result.intValue());
    }

    @Test
    public void extractBuildNumber_noBuildKeyword_returnsNull() {
        assertNull(VersionUtils.extractBuildNumber("version 1.2.3"));
    }

    @Test
    public void extractBuildNumber_emptyString_returnsNull() {
        assertNull(VersionUtils.extractBuildNumber(""));
    }

    @Test
    public void extractBuildNumber_nullInput_returnsNull() {
        assertNull(VersionUtils.extractBuildNumber(null));
    }

    @Test
    public void extractBuildNumber_buildWithoutNumber_returnsNull() {
        // "Build" without digits
        assertNull(VersionUtils.extractBuildNumber("Build without number"));
    }
}
