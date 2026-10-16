/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.sim;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import com.android.internal.annotations.GuardedBy;

/**
 * Defers automatic SIM selection dialogs while a privileged client changes SIM modes.
 *
 * <p>Clients must wait for the service connection before changing subscriptions and unbind when
 * the operation finishes. Android also removes bindings when a client process dies. The last
 * unbind replays the most recent dialog request, which is validated against current subscriptions.
 */
public class SimDialogSuppressionService extends Service {
    private static final Object LOCK = new Object();
    @GuardedBy("LOCK")
    private static int sBindingCount;
    @GuardedBy("LOCK")
    private static Intent sPendingIntent;

    @Override
    public IBinder onBind(Intent intent) {
        synchronized (LOCK) {
            sBindingCount++;
        }
        return new Binder();
    }

    @Override
    public void onRebind(Intent intent) {
        synchronized (LOCK) {
            sBindingCount++;
        }
    }

    static boolean deferDialog(Intent intent) {
        synchronized (LOCK) {
            if (sBindingCount == 0) {
                return false;
            }
            sPendingIntent = new Intent(intent);
            return true;
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        final Intent pending;
        synchronized (LOCK) {
            if (sBindingCount == 0) {
                return true;
            }
            sBindingCount--;
            if (sBindingCount != 0) {
                return true;
            }
            pending = sPendingIntent;
            sPendingIntent = null;
        }
        if (pending != null) {
            PrimarySubscriptionListChangedService.scheduleJob(this, pending);
        }
        return true;
    }
}
