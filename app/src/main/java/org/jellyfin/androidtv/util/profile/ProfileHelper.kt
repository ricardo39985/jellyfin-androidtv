package org.jellyfin.androidtv.util.profile

import org.jellyfin.androidtv.constant.CodecTypes
import org.jellyfin.androidtv.constant.ContainerTypes
import org.jellyfin.androidtv.util.DeviceUtils
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.apiclient.model.dlna.CodecProfile
import org.jellyfin.apiclient.model.dlna.CodecType
import org.jellyfin.apiclient.model.dlna.DeviceProfile
import org.jellyfin.apiclient.model.dlna.DirectPlayProfile
import org.jellyfin.apiclient.model.dlna.DlnaProfileType
import org.jellyfin.apiclient.model.dlna.ProfileCondition
import org.jellyfin.apiclient.model.dlna.ProfileConditionType
import org.jellyfin.apiclient.model.dlna.ProfileConditionValue
import org.jellyfin.apiclient.model.dlna.SubtitleDeliveryMethod
import org.jellyfin.apiclient.model.dlna.SubtitleProfile
import timber.log.Timber

object ProfileHelper {
	// H264 codec levels https://en.wikipedia.org/wiki/Advanced_Video_Coding#Levels
	private const val H264_LEVEL_4_1 = "41"
	private const val H264_LEVEL_5_1 = "51"
	private const val H264_LEVEL_5_2 = "52"

	private val MediaTest by lazy { MediaCodecCapabilitiesTest() }

	val deviceHevcCodecProfile by lazy {
		CodecProfile().apply {
			type = CodecType.Video
			codec = CodecTypes.HEVC

			conditions = when {
				!MediaTest.supportsHevc() -> {
					// The following condition is a method to exclude all HEVC
					Timber.i("*** Does NOT support HEVC")
					arrayOf(
						ProfileCondition(
							ProfileConditionType.Equals,
							ProfileConditionValue.VideoProfile,
							"none"
						)
					)
				}
				!MediaTest.supportsHevcMain10() -> {
					Timber.i("*** Does NOT support HEVC 10 bit")
					arrayOf(
						ProfileCondition(
							ProfileConditionType.NotEquals,
							ProfileConditionValue.VideoProfile,
							"Main 10"
						)
					)
				}
				else -> {
					// supports all HEVC
					Timber.i("*** Supports HEVC 10 bit")
					arrayOf(
						ProfileCondition(
							ProfileConditionType.NotEquals,
							ProfileConditionValue.VideoProfile,
							"none"
						)
					)
				}
			}
		}
	}

	val h264VideoLevelProfileCondition by lazy {
		ProfileCondition(
			ProfileConditionType.LessThanEqual,
			ProfileConditionValue.VideoLevel,
			when {
				// https://developer.amazon.com/docs/fire-tv/device-specifications.html
				DeviceUtils.isFireTvStick4k() -> H264_LEVEL_5_2
				DeviceUtils.isFireTv() -> H264_LEVEL_4_1
				else -> H264_LEVEL_5_1
			}
		)
	}

	val h264VideoProfileCondition by lazy {
		ProfileCondition(
			ProfileConditionType.EqualsAny,
			ProfileConditionValue.VideoProfile,
			arrayOf(
				"high",
				"main",
				"baseline",
				"constrained baseline"
			).joinToString("|")
		)
	}

	val photoDirectPlayProfile by lazy {
		DirectPlayProfile().apply {
			type = DlnaProfileType.Photo
			container = arrayOf(
				"jpg",
				"jpeg",
				"png",
				"gif",
				"webp"
			).joinToString(",")
		}
	}

	fun audioDirectPlayProfile(vararg containers: String) = DirectPlayProfile()
		.apply {
			type = DlnaProfileType.Audio
			container = containers.joinToString(",")
		}

	fun maxAudioChannelsCodecProfile(channels: Int) = CodecProfile()
		.apply {
			type = CodecType.VideoAudio
			conditions = arrayOf(
				ProfileCondition(
					ProfileConditionType.LessThanEqual,
					ProfileConditionValue.AudioChannels,
					channels.toString()
				)
			)
		}

	@JvmStatic
	fun addAc3Streaming(profile: DeviceProfile, primary: Boolean) {
		if (Utils.downMixAudio()) return

		val mkvProfile = findTranscodingProfile(profile, ContainerTypes.MKV) ?: return

		Timber.i("*** Adding AC3 as supported transcoded audio")
		mkvProfile.audioCodec = when (primary) {
			true -> "${CodecTypes.AC3},${mkvProfile.audioCodec}"
			false -> "${mkvProfile.audioCodec},${CodecTypes.AC3}"
		}
	}

	private fun findTranscodingProfile(
		deviceProfile: DeviceProfile,
		container: String
	) = deviceProfile.transcodingProfiles.find { it.container == container }

	internal fun subtitleProfile(
		format: String,
		method: SubtitleDeliveryMethod
	) = SubtitleProfile().apply {
		this.format = format
		this.method = method
	}
}
