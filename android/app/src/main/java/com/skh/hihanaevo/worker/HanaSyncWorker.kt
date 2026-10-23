package com.skh.hihanaevo.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.skh.hihanaevo.AlimNotifier
import com.skh.hihanaevo.BoardWatch
import com.skh.hihanaevo.HanaAcademicApi
import com.skh.hihanaevo.HanaCredentialStore
import com.skh.hihanaevo.HanaMealSync
import com.skh.hihanaevo.HanaPortalClient
import com.skh.hihanaevo.HanaPortalException
import com.skh.hihanaevo.HanaSyncApplier
import com.skh.hihanaevo.HanaSyncGate
import com.skh.hihanaevo.HanaTimetableSync
import com.skh.hihanaevo.PlanStore
import com.skh.hihanaevo.widget.HiHanaEvoWidget

/**
 * WorkManager 가 주기적으로 돌리는 하이하나 자동 동기화.
 *
 * Android 는 Doze/배터리 최적화 때문에 실제 실행 주기가 설정값보다 늦어질 수 있습니다 —
 * PeriodicWorkRequest 의 간격은 "이 정도마다 시도해줘" 라는 목표치일 뿐입니다.
 */
class HanaSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val today = PlanStore.today()

        // 급식은 로그인 없이도 받을 수 있어 계정/게이트와 무관하게 먼저 시도합니다.
        // 실패해도 전날 캐시를 유지하고 동기화 자체는 계속 진행합니다.
        runCatching { HanaMealSync.refresh(applicationContext, today) }

        if (!HanaCredentialStore.hasCredentials(applicationContext)) return Result.success()
        if (HanaSyncGate.isSuspended(applicationContext)) return Result.success()

        return try {
            // 일반 조회 실패는 다음 단계에 영향을 주지 않지만, 로그인 실패는 즉시 중단해
            // 한 번의 Worker 실행에서 비밀번호 오류를 여러 번 보내지 않습니다.
            try {
                val alims = HanaAcademicApi.fetchAlim(applicationContext)
                AlimNotifier.process(applicationContext, alims)
            } catch (e: HanaPortalException.LoginFailed) {
                throw e
            } catch (_: Exception) {
                // 알리미 실패와 면학 동기화는 서로 독립입니다.
            }

            // 게시판 새 글 알림 — 5분 확인(BoardWatch)과 같은 확인을 한 번 더(알람이 밀렸을 때 대비). 겹쳐도 잠금으로 한 번만 알립니다.
            BoardWatch.check(applicationContext)

            val sync = HanaPortalClient.get().fetchDailySync(applicationContext, today)
            HanaSyncApplier.apply(applicationContext, sync, today)
            // 장소 배정과 함께 시간표도 강제로 새로 받아 설치합니다 (실패 시 캐시 유지).
            HanaTimetableSync.refresh(applicationContext, today, force = true)
            HanaSyncGate.clear(applicationContext)
            PlanStore.markSynced(applicationContext)
            HiHanaEvoWidget().updateAll(applicationContext)
            runCatching { com.skh.hihanaevo.LiveActivity.update(applicationContext) }
            Result.success()
        } catch (e: HanaPortalException.LoginFailed) {
            // 비밀번호가 틀렸는데 계속 재시도하면 5회 실패 잠금 위험 — 사용자가 앱을 열어
            // 직접 확인할 때까지 자동 동기화를 멈춥니다 (수동 새로고침은 이 게이트의 영향을 받지 않습니다)
            HanaSyncGate.suspend(applicationContext)
            Result.success()
        } catch (e: Exception) {
            // 네트워크 오류 등은 일시적일 수 있으니, WorkManager 자체 재시도 대신 다음 주기를 기다립니다
            // (재시도를 남발하면 로그인 시도 횟수가 불필요하게 늘어날 수 있어서요)
            Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "hana_sync_periodic"
    }
}
