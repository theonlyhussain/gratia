package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

data class CreditPerson(
    val name: String,
    val roles: List<String>
)

@Composable
fun CreditsCard(
    credits: List<CreditPerson>,
    onShowAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (credits.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(GratiaTheme.colors.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Credits",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = GratiaTheme.colors.textPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Show top 5 credits initially
            val visibleCredits = credits.take(5)
            
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                visibleCredits.forEach { person ->
                    Column {
                        Text(
                            text = person.name,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = GratiaTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = person.roles.joinToString(" • "),
                            fontFamily = Inter,
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            color = GratiaTheme.colors.textSecondary
                        )
                    }
                }
            }

            if (credits.size > 5) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Show all",
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = GratiaTheme.colors.textPrimary,
                    modifier = Modifier.clickable { onShowAllClick() }
                )
            }
        }
    }
}
