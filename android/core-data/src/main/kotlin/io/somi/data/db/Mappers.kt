package io.somi.data.db

import io.somi.common.chat.Author
import io.somi.common.chat.Message

/**
 * Row ↔ domain converters. Kept in core-data because [MessageEntity]
 * is core-data's private shape; [Message] from core-common is the
 * domain vocabulary every other module speaks.
 */
internal fun MessageEntity.toDomain(): Message = Message(
    id = id,
    author = Author.valueOf(author),
    text = text,
    timestamp = timestamp,
)

internal fun Message.toEntityForInsert(): MessageEntity = MessageEntity(
    id = 0L,
    author = author.name,
    text = text,
    timestamp = timestamp,
)
