import androidx.compose.runtime.*

@Composable
fun <T> GranularObserver(
    state: State<BatteryState>,
    selector: (BatteryState) -> T,
    content: @Composable (T) -> Unit
) {
    val value by remember { derivedStateOf { selector(state.value) } }
    content(value)
}
