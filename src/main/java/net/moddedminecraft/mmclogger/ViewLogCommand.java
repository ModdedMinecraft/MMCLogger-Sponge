package net.moddedminecraft.mmclogger;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.service.pagination.PaginationService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class ViewLogCommand implements CommandExecutor {

    private final Main plugin;

    public ViewLogCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        Optional<User> playerOp = args.<User>getOne("player");
        PaginationService paginationService = Sponge.getServiceManager().provide(PaginationService.class).get();
        java.util.List<Text> contents = new ArrayList<>();
        String folderDir = plugin.rootFolder.getName();
        File[] files = plugin.rootFolder.listFiles();

        if (playerOp.isPresent()) {
            getHaste(new File(plugin.playersFolder + "/" + playerOp.get().getName() + ".log"), src);
            return CommandResult.success();
        } else {

            for (File file : files) {
                Text.Builder send = Text.builder();
                if (file.isDirectory()) {
                    send.append(plugin.fromLegacy("&8[&e" + file.getName() + "&8]"));
                    send.onClick(TextActions.executeCallback(viewDir(file)));
                    send.onHover(TextActions.showText(plugin.fromLegacy("&eClick to view folder")));
                } else {
                    send.append(plugin.fromLegacy("&e" + file.getName()));
                    send.onClick(TextActions.executeCallback(viewFile(file)));
                    send.onHover(TextActions.showText(plugin.fromLegacy("&eClick to view this log in hastebin")));
                }
                contents.add(send.build());
            }

            paginationService.builder()
                    .title(plugin.fromLegacy("&8[&7" + folderDir + "&8]"))
                    .contents(contents)
                    .padding(plugin.fromLegacy("&8="))
                    .sendTo(src);
            return CommandResult.success();
        }
    }


    private Consumer<CommandSource> viewDir(File folder) {
        return consumer -> {
            PaginationService paginationService = Sponge.getServiceManager().provide(PaginationService.class).get();
            java.util.List<Text> contents = new ArrayList<>();
            Sponge.getScheduler().createTaskBuilder().execute(() -> {
                for (File file : folder.listFiles()) {
                    Text.Builder send = Text.builder();
                    if (file.isDirectory()) {
                        send.append(plugin.fromLegacy("&8[&e" + file.getName() + "&8]"));
                        send.onClick(TextActions.executeCallback(viewDir(file)));
                        send.onHover(TextActions.showText(plugin.fromLegacy("&eClick to view folder")));
                    } else {
                        send.append(plugin.fromLegacy("&e" + file.getName()));
                        send.onClick(TextActions.executeCallback(viewFile(file)));
                        send.onHover(TextActions.showText(plugin.fromLegacy("&eClick to get a hastebin link for this file")));
                    }
                    contents.add(send.build());
                }

                paginationService.builder()
                        .title(plugin.fromLegacy("&8[&7" + folder.getName() + "&8]"))
                        .contents(contents)
                        .padding(plugin.fromLegacy("&8="))
                        .sendTo(consumer);
            }).name("mmclogger-async-viewdir").async().submit(plugin);
        };
    }

    private Consumer<CommandSource> viewFile(File file) {
        return consumer -> {
            getHaste(file, consumer);
        };
    }

    private void getHaste(File file, CommandSource src) {
        Sponge.getScheduler().createTaskBuilder().execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("https://hastebin.com/documents").openConnection();
                connection.setRequestMethod("POST");
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");

                try(DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                    List<String> fileList = Files.readAllLines(file.toPath(), getCharSet());
                    StringBuilder sb = new StringBuilder();
                    int numLines = fileList.size();
                    if (numLines > 6000) {
                        numLines = 6000;
                    }
                    List<String> lastLines = fileList.subList(fileList.size() - numLines, fileList.size());
                    for (String line : lastLines) {
                        sb.append(line);
                        sb.append("\n");
                    }
                    wr.write(sb.toString().getBytes(getCharSet()));
                    wr.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                StringBuilder response = new StringBuilder();
                try (BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String inputLine;
                    while ((inputLine = rd.readLine()) != null) response.append(inputLine);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                JsonElement json = new JsonParser().parse(response.toString());
                if (!json.isJsonObject()) {
                    throw new IOException("Can't parse JSON");
                }

                Text.Builder send = Text.builder();
                String url = "http://hastebin.com/" + json.getAsJsonObject().get("key").getAsString();
                send.append(plugin.fromLegacy("&3" + url));
                send.onClick(TextActions.openUrl(new URL(url)));
                send.onHover(TextActions.showText(plugin.fromLegacy("&eClick here to go to: &6" + url)));
                src.sendMessage(send.build());
            } catch (IOException e) {
                e.printStackTrace();
                src.sendMessage(plugin.fromLegacy("&cThere was an error getting your hastebin"));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).name("mmclogger-async-gethaste").async().submit(plugin);
    }

    private Charset getCharSet() {
        switch(plugin.config().vclCharset) {
            case "ISO-8859-1":
                return StandardCharsets.ISO_8859_1;
            case "UTF-8":
                return StandardCharsets.UTF_8;
            default:
                return StandardCharsets.UTF_8;
        }
    }
}
