package com.noobexon.xposedfakelocation.manager.ui.map

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.ui.components.AppDialog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Dialog that lets the user jump to an arbitrary coordinate using one field.
 *
 * Supported inputs:
 * - coordinates: "-15.029552, 40.2055142" / "-15.029552 40.2055142" / "-15.029552; 40.2055142"
 * - an IPv4/IPv6 address, resolved to its approximate location by the IP lookup.
 */
@Composable
fun GoToPointDialog(
    query: String,
    @StringRes queryErrorRes: Int?,
    onQueryChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppDialog(
        title = stringResource(R.string.map_go_to_point),
        onDismissRequest = onDismissRequest,
        confirmText = stringResource(R.string.action_go),
        onConfirm = onConfirm,
        dismissText = stringResource(R.string.action_cancel),
    ) {
        Column {
            CoordinateInputField(
                value = query,
                onValueChange = onQueryChange,
                label = stringResource(R.string.field_coordinates),
                errorRes = queryErrorRes,
                keyboardType = KeyboardType.Text,
            )

            Text(
                text = stringResource(R.string.field_coordinates_hint),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }
    }
}

/**
 * Dialog that lets the user save the current spoof location as a named favourite.
 */
@Composable
fun AddToFavoritesDialog(
    name: String,
    description: String,
    latitude: String,
    longitude: String,
    @StringRes nameErrorRes: Int?,
    @StringRes latitudeErrorRes: Int?,
    @StringRes longitudeErrorRes: Int?,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppDialog(
        title = stringResource(R.string.map_add_to_favorites),
        onDismissRequest = onDismissRequest,
        confirmText = stringResource(R.string.action_add),
        onConfirm = onConfirm,
        dismissText = stringResource(R.string.action_cancel),
    ) {
        Column {
            CoordinateInputField(
                value = name,
                onValueChange = onNameChange,
                label = stringResource(R.string.field_name),
                errorRes = nameErrorRes,
            )
            Spacer(modifier = Modifier.height(10.dp))
            CoordinateInputField(
                value = description,
                onValueChange = onDescriptionChange,
                label = stringResource(R.string.field_description),
                errorRes = null,
            )
            Spacer(modifier = Modifier.height(10.dp))
            CoordinateInputField(
                value = latitude,
                onValueChange = onLatitudeChange,
                label = stringResource(R.string.field_latitude),
                errorRes = latitudeErrorRes,
                keyboardType = KeyboardType.Number,
            )
            Spacer(modifier = Modifier.height(10.dp))
            CoordinateInputField(
                value = longitude,
                onValueChange = onLongitudeChange,
                label = stringResource(R.string.field_longitude),
                errorRes = longitudeErrorRes,
                keyboardType = KeyboardType.Number,
            )
        }
    }
}

/**
 * A single labelled miuix [TextField] with inline error annotation below.
 */
@Composable
private fun CoordinateInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    @StringRes errorRes: Int?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Unspecified,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth()
    )
    if (errorRes != null) {
        Text(
            text = stringResource(errorRes),
            color = MiuixTheme.colorScheme.error,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
    }
}
