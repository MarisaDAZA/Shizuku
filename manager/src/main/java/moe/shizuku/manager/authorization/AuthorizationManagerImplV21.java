package moe.shizuku.manager.authorization;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.v4.util.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import moe.shizuku.manager.Manifest;

/**
 * Created by rikka on 2017/10/23.
 */

public class AuthorizationManagerImplV21 implements AuthorizationManagerImpl {

    private static final String NAME = "settings";
    private static final String KEY = "granted_packages";

    private static SharedPreferences sSharedPreferences;
    private static Set<Pair<String, Long>> sPackages;

    private static void init(Context context) {
        if (sSharedPreferences == null) {
            sSharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
            sPackages = new HashSet<>();
            sPackages = fromPreference(context, sSharedPreferences.getStringSet(KEY, new HashSet<String>()));
        }
    }

    private static boolean granted(String packageName) {
        if (packageName == null) {
            return false;
        }
        for (Pair<String, Long> p : sPackages) {
            if (p.first.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private static void set(String packageName, long firstInstallTime, boolean grant) {
        if (grant) {
            grant(packageName, firstInstallTime);
        } else {
            revoke(packageName);
        }
    }

    private static void remove(String packageName) {
        for (Pair<String, Long> p : new HashSet<>(sPackages)) {
            if (p.first.equals(packageName)) {
                sPackages.remove(p);
            }
        }
    }

    private static void grant(String packageName, long firstInstallTime) {
        remove(packageName);

        sPackages.add(new Pair<>(packageName, firstInstallTime));
        sSharedPreferences.edit()
                .putStringSet(KEY, toPreference(sPackages))
                .apply();
    }

    private static void revoke(String packageName) {
        remove(packageName);

        sSharedPreferences.edit()
                .putStringSet(KEY, toPreference(sPackages))
                .apply();
    }

    private static Set<Pair<String, Long>> fromPreference(Context context, Set<String> from) {
        PackageManager pm = context.getPackageManager();

        Set<Pair<String, Long>> to = new HashSet<>();
        for (String p: from) {
            String[] temp = p.split("\\|");
            if (temp.length == 2) {
                String packageName = temp[0];
                long firstInstallTime = Long.parseLong(temp[1]);

                try {
                    PackageInfo pi = pm.getPackageInfo(packageName, 0);

                    boolean permission = false;
                    if (pi.requestedPermissions != null) {
                        for (String perm : pi.requestedPermissions) {
                            if (Manifest.permission.API.equals(perm)) {
                                permission = true;
                                break;
                            }
                        }
                    }

                    if (pi.firstInstallTime == firstInstallTime
                            && permission) {
                        to.add(new Pair<>(packageName, firstInstallTime));
                    }
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
        }
        return to;
    }

    private static Set<String> toPreference(Set<Pair<String, Long>> from) {
        Set<String> to = new HashSet<>();
        for (Pair<String, Long> p: from) {
            to.add(p.first + "|" + p.second);
        }
        return to;
    }

    @Override
    public List<String> getPackages(Context context) {
        List<String> packages = new ArrayList<>();

        for (PackageInfo pi : context.getPackageManager().getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            if (pi.requestedPermissions != null) {
                for (String p : pi.requestedPermissions) {
                    if (Manifest.permission.API.equals(p)) {
                        packages.add(pi.packageName);
                        break;
                    }
                }
            }
        }
        return packages;
    }

    @Override
    public boolean granted(Context context, String packageName) {
        init(context);
        return granted(packageName);
    }

    @Override
    public void grant(Context context, String packageName) {
        init(context);

        try {
            grant(packageName, context.getPackageManager().getPackageInfo(packageName, 0).firstInstallTime);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    @Override
    public void revoke(Context context, String packageName) {
        init(context);
        revoke(packageName);
    }
}
