package top.cnpclines.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;

public final class UnquotedStringArgument implements ArgumentType<String> {
    private static final SimpleCommandExceptionType UNTERMINATED = new SimpleCommandExceptionType(
        new LiteralMessage("Unterminated quoted string")
    );
    public static final Info INFO = new Info();

    private UnquotedStringArgument() {
    }

    public static UnquotedStringArgument unquotedString() {
        return new UnquotedStringArgument();
    }

    public static String getString(CommandContext<?> context, String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '"') {
            return readQuoted(reader);
        }
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    private static String readQuoted(StringReader reader) throws CommandSyntaxException {
        reader.skip();
        StringBuilder result = new StringBuilder();
        while (reader.canRead()) {
            char c = reader.read();
            if (c == '"') {
                return result.toString();
            }
            if (c == '\\' && reader.canRead()) {
                char next = reader.peek();
                if (next == '"' || next == '\\') {
                    result.append(reader.read());
                    continue;
                }
            }
            result.append(c);
        }
        throw UNTERMINATED.create();
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("hello", "#FF5555", "value");
    }

    public static final class Info
        implements ArgumentTypeInfo<UnquotedStringArgument, UnquotedStringArgument.Template> {
        @Override
        public void serializeToNetwork(UnquotedStringArgument.Template template, FriendlyByteBuf buffer) {
        }

        @Override
        public UnquotedStringArgument.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
            return new UnquotedStringArgument.Template();
        }

        @Override
        public void serializeToJson(UnquotedStringArgument.Template template, JsonObject json) {
        }

        @Override
        public UnquotedStringArgument.Template unpack(UnquotedStringArgument argument) {
            return new UnquotedStringArgument.Template();
        }
    }

    public static final class Template implements ArgumentTypeInfo.Template<UnquotedStringArgument> {
        @Override
        public UnquotedStringArgument instantiate(CommandBuildContext context) {
            return new UnquotedStringArgument();
        }

        @Override
        public ArgumentTypeInfo<UnquotedStringArgument, ?> type() {
            return INFO;
        }
    }
}
