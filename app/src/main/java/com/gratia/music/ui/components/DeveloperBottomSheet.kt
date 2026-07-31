package com.gratia.music.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

val GithubIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Github",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 2f)
            curveTo(6.477f, 2f, 2f, 6.477f, 2f, 12f)
            curveTo(2f, 16.418f, 4.865f, 20.166f, 8.84f, 21.49f)
            curveTo(9.34f, 21.582f, 9.52f, 21.272f, 9.52f, 21.006f)
            curveTo(9.52f, 20.767f, 9.51f, 19.982f, 9.505f, 19.117f)
            curveTo(6.724f, 19.72f, 6.138f, 17.962f, 6.138f, 17.962f)
            curveTo(5.683f, 16.809f, 5.029f, 16.502f, 5.029f, 16.502f)
            curveTo(4.124f, 15.882f, 5.097f, 15.894f, 5.097f, 15.894f)
            curveTo(6.098f, 15.965f, 6.623f, 16.924f, 6.623f, 16.924f)
            curveTo(7.513f, 18.447f, 8.956f, 18.006f, 9.542f, 17.747f)
            curveTo(9.633f, 17.086f, 9.9f, 16.646f, 10.196f, 16.397f)
            curveTo(7.977f, 16.145f, 5.647f, 15.286f, 5.647f, 11.472f)
            curveTo(5.647f, 10.385f, 6.035f, 9.497f, 6.665f, 8.8f)
            curveTo(6.564f, 8.548f, 6.223f, 7.536f, 6.762f, 6.166f)
            curveTo(6.762f, 6.166f, 7.592f, 5.901f, 9.495f, 7.189f)
            curveTo(10.283f, 6.97f, 11.141f, 6.861f, 12f, 6.857f)
            curveTo(12.859f, 6.861f, 13.717f, 6.97f, 14.505f, 7.189f)
            curveTo(16.407f, 5.901f, 17.236f, 6.166f, 17.236f, 6.166f)
            curveTo(17.777f, 7.536f, 17.436f, 8.548f, 17.335f, 8.8f)
            curveTo(17.967f, 9.497f, 18.351f, 10.385f, 18.351f, 11.472f)
            curveTo(18.351f, 15.295f, 16.017f, 16.141f, 13.79f, 16.386f)
            curveTo(14.161f, 16.706f, 14.492f, 17.334f, 14.492f, 18.3f)
            curveTo(14.492f, 19.684f, 14.48f, 20.803f, 14.48f, 21.006f)
            curveTo(14.48f, 21.275f, 14.657f, 21.587f, 15.167f, 21.488f)
            curveTo(19.138f, 20.162f, 22f, 16.416f, 22f, 12f)
            curveTo(22f, 6.477f, 17.523f, 2f, 12f, 2f)
            close()
        }
    }.build()

val InstagramIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Instagram",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 2.163f)
            curveTo(15.204f, 2.163f, 15.584f, 2.175f, 16.85f, 2.233f)
            curveTo(18.044f, 2.288f, 18.667f, 2.47f, 19.083f, 2.632f)
            curveTo(19.633f, 2.846f, 20.026f, 3.1f, 20.438f, 3.513f)
            curveTo(20.851f, 3.925f, 21.105f, 4.318f, 21.319f, 4.868f)
            curveTo(21.481f, 5.284f, 21.663f, 5.908f, 21.718f, 7.102f)
            curveTo(21.776f, 8.368f, 21.788f, 8.748f, 21.788f, 11.952f)
            curveTo(21.788f, 15.156f, 21.776f, 15.536f, 21.718f, 16.802f)
            curveTo(21.663f, 17.996f, 21.481f, 18.619f, 21.319f, 19.035f)
            curveTo(21.105f, 19.585f, 20.851f, 19.978f, 20.438f, 20.39f)
            curveTo(20.026f, 20.803f, 19.633f, 21.057f, 19.083f, 21.271f)
            curveTo(18.667f, 21.434f, 18.044f, 21.615f, 16.85f, 21.67f)
            curveTo(15.584f, 21.728f, 15.204f, 21.74f, 12f, 21.74f)
            curveTo(8.796f, 21.74f, 8.416f, 21.728f, 7.15f, 21.67f)
            curveTo(5.956f, 21.615f, 5.333f, 21.434f, 4.917f, 21.271f)
            curveTo(4.367f, 21.057f, 3.974f, 20.803f, 3.562f, 20.39f)
            curveTo(3.15f, 19.978f, 2.895f, 19.585f, 2.681f, 19.035f)
            curveTo(2.519f, 18.619f, 2.337f, 17.996f, 2.282f, 16.802f)
            curveTo(2.224f, 15.536f, 2.212f, 15.156f, 2.212f, 11.952f)
            curveTo(2.212f, 8.748f, 2.224f, 8.368f, 2.282f, 7.102f)
            curveTo(2.337f, 5.908f, 2.519f, 5.284f, 2.681f, 4.868f)
            curveTo(2.895f, 4.318f, 3.15f, 3.925f, 3.562f, 3.513f)
            curveTo(3.974f, 3.1f, 4.367f, 2.846f, 4.917f, 2.632f)
            curveTo(5.333f, 2.47f, 5.956f, 2.288f, 7.15f, 2.233f)
            curveTo(8.416f, 2.175f, 8.796f, 2.163f, 12f, 2.163f)
            moveTo(12f, 0f)
            curveTo(8.741f, 0f, 8.333f, 0.014f, 7.053f, 0.072f)
            curveTo(5.775f, 0.131f, 4.902f, 0.333f, 4.14f, 0.63f)
            curveTo(3.354f, 0.935f, 2.688f, 1.353f, 2.022f, 2.019f)
            curveTo(1.356f, 2.686f, 0.938f, 3.352f, 0.633f, 4.138f)
            curveTo(0.336f, 4.899f, 0.134f, 5.772f, 0.075f, 7.05f)
            curveTo(0.017f, 8.33f, 0.003f, 8.738f, 0.003f, 11.997f)
            curveTo(0.003f, 15.256f, 0.017f, 15.665f, 0.075f, 16.944f)
            curveTo(0.134f, 18.222f, 0.336f, 19.096f, 0.633f, 19.857f)
            curveTo(0.938f, 20.643f, 1.356f, 21.309f, 2.022f, 21.975f)
            curveTo(2.688f, 22.641f, 3.354f, 23.06f, 4.14f, 23.365f)
            curveTo(4.901f, 23.662f, 5.774f, 23.863f, 7.053f, 23.923f)
            curveTo(8.332f, 23.981f, 8.74f, 23.995f, 11.999f, 23.995f)
            curveTo(15.258f, 23.995f, 15.666f, 23.981f, 16.946f, 23.923f)
            curveTo(18.224f, 23.864f, 19.097f, 23.662f, 19.859f, 23.365f)
            curveTo(20.645f, 23.06f, 21.31f, 22.642f, 21.977f, 21.975f)
            curveTo(22.643f, 21.309f, 23.061f, 20.643f, 23.366f, 19.857f)
            curveTo(23.663f, 19.096f, 23.865f, 18.222f, 23.924f, 16.944f)
            curveTo(23.982f, 15.665f, 23.996f, 15.256f, 23.996f, 11.997f)
            curveTo(23.996f, 8.738f, 23.982f, 8.33f, 23.924f, 7.05f)
            curveTo(23.865f, 5.772f, 23.663f, 4.899f, 23.366f, 4.138f)
            curveTo(23.061f, 3.352f, 22.643f, 2.686f, 21.977f, 2.019f)
            curveTo(21.31f, 1.353f, 20.645f, 0.935f, 19.859f, 0.63f)
            curveTo(19.097f, 0.333f, 18.224f, 0.131f, 16.946f, 0.072f)
            curveTo(15.666f, 0.014f, 15.258f, 0f, 11.999f, 0f)
            close()
            moveTo(12f, 5.838f)
            curveTo(8.597f, 5.838f, 5.838f, 8.597f, 5.838f, 12f)
            curveTo(5.838f, 15.403f, 8.597f, 18.162f, 12f, 18.162f)
            curveTo(15.403f, 18.162f, 18.162f, 15.403f, 18.162f, 12f)
            curveTo(18.162f, 8.597f, 15.403f, 5.838f, 12f, 5.838f)
            close()
            moveTo(12f, 15.998f)
            curveTo(9.792f, 15.998f, 8.002f, 14.208f, 8.002f, 12f)
            curveTo(8.002f, 9.792f, 9.792f, 8.002f, 12f, 8.002f)
            curveTo(14.208f, 8.002f, 15.998f, 9.792f, 15.998f, 12f)
            curveTo(15.998f, 14.208f, 14.208f, 15.998f, 12f, 15.998f)
            close()
            moveTo(19.853f, 5.586f)
            curveTo(19.853f, 6.381f, 19.208f, 7.026f, 18.413f, 7.026f)
            curveTo(17.618f, 7.026f, 16.973f, 6.381f, 16.973f, 5.586f)
            curveTo(16.973f, 4.791f, 17.618f, 4.146f, 18.413f, 4.146f)
            curveTo(19.208f, 4.146f, 19.853f, 4.791f, 19.853f, 5.586f)
            close()
        }
    }.build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GratiaTheme.colors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Picture
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("https://github.com/theonlyhussain.png")
                    .crossfade(true)
                    .build(),
                contentDescription = "Hussain Shaikh",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            )

            Spacer(Modifier.height(16.dp))

            // Name
            Text(
                text = "Hussain Shaikh",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White
            )

            Spacer(Modifier.height(8.dp))

            // Bio
            Text(
                text = "I make what I feel",
                fontFamily = Inter,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(Modifier.height(32.dp))

            // Links/Logos Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Portfolio
                DeveloperActionIcon(
                    icon = Icons.Default.Language,
                    contentDescription = "Portfolio",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://theonlyhussain.vercel.app"))
                        context.startActivity(intent)
                    }
                )
                
                // Instagram
                DeveloperActionIcon(
                    icon = InstagramIcon,
                    contentDescription = "Instagram",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/theonly.hussain"))
                        context.startActivity(intent)
                    }
                )
                
                // GitHub
                DeveloperActionIcon(
                    icon = GithubIcon,
                    contentDescription = "GitHub",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/theonlyhussain"))
                        context.startActivity(intent)
                    }
                )

                // Mail
                DeveloperActionIcon(
                    icon = Icons.Default.Email,
                    contentDescription = "Email",
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:hussainshaikh2509@gmail.com")
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
private fun DeveloperActionIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(GratiaTheme.colors.surfaceHover)
            .bounceClick { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = GratiaTheme.colors.textPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}
