import kr.lanthanide.wanderland.animation.*
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.LightingChunk
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import org.joml.Vector3f

fun main() {
    // Minestom 서버 초기화
    val minecraftServer = MinecraftServer.init()

    val instanceManager = MinecraftServer.getInstanceManager()
    val instanceContainer = instanceManager.createInstanceContainer() // 기본 인스턴스 생성
    instanceContainer.setChunkSupplier(::LightingChunk)
    instanceContainer.enableAutoChunkLoad(true)

    // AnimatedObject를 저장할 변수 (플레이어 접속 시 스폰)
    var testAnimatedObject: AnimatedObject? = null

    // 플레이어 접속 시 AnimatedObject 설정 및 스폰
    MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
        val player = event.player
        event.spawningInstance = instanceContainer

        player.gameMode = GameMode.CREATIVE
    }

    MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent::class.java) { event ->
        val player = event.player
        player.teleport(Pos(0.0, 72.0, 0.0)) // 플레이어 위치 설정

        if (testAnimatedObject == null) {
            testAnimatedObject = createSampleAnimatedObject(player) // AnimatedObject 생성
            // AnimatedObject 스폰 위치 설정 (플레이어 근처)
            val spawnPos = player.position.add(2.0, 0.0, 0.0)
            testAnimatedObject.spawn(instanceContainer, spawnPos)
            testAnimatedObject.playAnimation("bobbing", loopOverride = true) // "bobbing" 애니메이션 재생
        }
    }

    // 서버 시작
    minecraftServer.start("0.0.0.0", 25565)
    println("Test Minestom Server with AnimatedObject started!")
    println("Current time is: ${java.time.LocalDateTime.now()}")
}

/**
 * 예제용 AnimatedObject를 생성하는 함수
 */
fun createSampleAnimatedObject(playerContext: Player? = null): AnimatedObject {
    // 1. Bone 생성
    val moverBone = Bone(
        name = "mover_bone",
        // 초기 로컬 변환은 기본값 (위치 0,0,0 / 회전 항등 / 크기 1,1,1) 사용
    )

    // 2. Bone에 부착할 Minestom DisplayEntity 생성
    val displayEntity = Entity(EntityType.ITEM_DISPLAY) // ITEM_DISPLAY 사용
    (displayEntity.entityMeta as ItemDisplayMeta).apply {
        itemStack = ItemStack.of(Material.DIAMOND_BLOCK) // 다이아몬드 블록을 보여주도록 설정
        // 중요: DisplayEntity의 변환 보간 설정을 0으로 하여 즉시 반영되도록 하거나,
        // 애니메이션 틱 속도에 맞춰 부드럽게 변하도록 값을 줄 수 있습니다.
        // 여기서는 애니메이션 시스템이 매 틱 위치를 직접 제어하므로 0으로 설정.
        transformationInterpolationDuration = 0
        // setInterpolationStartDelta(0) // Minestom 최신 버전
        // setInterpolationDelay(0) // 이전 버전

        // billboard = Display.BillboardConstraints.FIXED // 필요에 따라 빌보드 설정
    }

    // 3. BoneAttachment 생성 및 Bone에 추가
    // Bone 클래스에 attachMinestomEntity 헬퍼 메소드가 있다면 사용해도 좋습니다.
    // 예: val attachment = moverBone.attachMinestomEntity(displayEntity)
    val attachment = BoneAttachment(displayEntity, moverBone)
    moverBone.attachments.add(attachment)


    // 4. Armature 생성 (단일 rootBone 사용)
    val armature = Armature(moverBone)

    // 5. AnimationClip 생성 ("bobbing" 애니메이션)
    val bobbingClip = AnimationClip(
        name = "bobbing",
        duration = 1.0f, // 1초 길이의 애니메이션
        loop = true, // 기본적으로 반복 설정 (playAnimation에서 오버라이드 가능)
        boneAnimations = mapOf(
            "mover_bone" to BoneAnimation( // "mover_bone"에 대한 애니메이션 정의
                boneName = "mover_bone",
                positionTrack = AnimationTrack(
                    listOf(
                        Keyframe(0.0f, Vector3f(0f, 0f, 0f)),                   // 시작: Y=0
                        Keyframe(0.5f, Vector3f(0f, 1f, 0f)),                   // 0.5초: Y=1 (위로 1칸)
                        Keyframe(1.0f, Vector3f(0f, 0f, 0f))                    // 1.0초: Y=0 (원위치)
                    )
                )
                // rotationTrack, scaleTrack은 이 예제에서 사용하지 않으므로 null (기본값)
            )
        )
    )

    // 6. AnimatedObject 생성
    return AnimatedObject(armature, mapOf("bobbing" to bobbingClip))
}