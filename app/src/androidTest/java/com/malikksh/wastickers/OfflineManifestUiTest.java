package com.malikksh.wastickers;

import static org.junit.Assert.assertFalse;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class OfflineManifestUiTest {
    @Test
    public void appDoesNotRequestInternetPermission() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_PERMISSIONS);
        List<String> permissions = info.requestedPermissions == null
                ? Collections.emptyList()
                : Arrays.asList(info.requestedPermissions);
        assertFalse(permissions.contains(Manifest.permission.INTERNET));
    }
}
