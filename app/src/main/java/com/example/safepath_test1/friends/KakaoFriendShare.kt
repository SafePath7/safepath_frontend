package com.example.safepath_test1.friends

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/** 카카오톡 수신자 선택 화면을 열며, 실제 전송 완료 여부는 확인하지 않는다. */
fun shareFriendInvite(context: Context, invite: CreatedFriendInvite): String? {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.kakao.talk")
            putExtra(
                Intent.EXTRA_TEXT,
                "${invite.senderName}님이 SafePath 친구 연결을 요청했어요.\n아래 링크에서 요청자 정보를 확인하고 수락해 주세요.\n${invite.url}",
            )
        }
    return try {
        context.startActivity(intent)
        null
    } catch (_: ActivityNotFoundException) {
        "카카오톡이 설치되어 있지 않아요. 카카오톡 설치 후 다시 열어 주세요."
    } catch (_: SecurityException) {
        "카카오톡을 열지 못했어요. 앱 상태를 확인하고 다시 시도해 주세요."
    }
}
