package collectionlogcommand;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.ItemComposition;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static net.runelite.client.util.Text.sanitize;

@Slf4j
@PluginDescriptor(
	name = "Collection Log Command",
	description = "Displays collection log data using a chat command.",
	tags = {"Collection", "Log", "Command"}
)
public class CollectionLogCommandPlugin extends Plugin
{
	private static final String COLLECTION_LOG_COMMAND_STRING = "!log";

	private Map<Integer, Integer> loadedCollectionLogIcons;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatCommandManager chatCommandManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private CollectionLogCommandApiClient apiClient;

	@Override
	protected void startUp()
	{
		loadedCollectionLogIcons = new HashMap<>();
		chatCommandManager.registerCommandAsync(COLLECTION_LOG_COMMAND_STRING, this::collectionLogLookup);
	}

	@Override
	protected void shutDown()
	{
		loadedCollectionLogIcons.clear();
	}

	/**
	 * Looks up and then replaces !log chat messages
	 *
	 * @param chatMessage The ChatMessage event
	 * @param message Text of the message
	 */
	private void collectionLogLookup(ChatMessage chatMessage, String message)
	{
		CollectionLog collectionLog;
		try
		{
			collectionLog = apiClient.getCollectionLog(sanitize(chatMessage.getName()));
		}
		catch (IOException e)
		{
			return;
		}
		clientThread.invoke(() ->
		{
			String replacementMessage;

			String[] commands = message.split("\\s+", 2);
			if (collectionLog == null)
			{
				replacementMessage = "No Collection Log data found for user.";
			}
			else if (commands.length != 2)
			{
				replacementMessage = "Collection Log: " + collectionLog.getUniqueObtained() + "/" + collectionLog.getUniqueItems();
			}
			else
			{
				String pageArgument = CollectionLogPage.aliasPageName(commands[1]);
				CollectionLogPage collectionLogPage;
				if (pageArgument.equals("any"))
				{
					collectionLogPage = collectionLog.randomPage();
				}
				else
				{
					collectionLogPage = collectionLog.searchForPage(pageArgument);
				}

				if (collectionLogPage == null)
				{
					replacementMessage = "No Collection Log page found.";
				}
				else
				{
					loadPageIcons(collectionLogPage.getItems());
					replacementMessage = buildMessageReplacement(collectionLogPage);
				}
			}

			chatMessage.getMessageNode().setValue(replacementMessage);
			client.runScript(ScriptID.BUILD_CHATBOX);
		});
	}

	/**
	 * Loads a list of Collection Log items into the client's mod icons.
	 *
	 * @param collectionLogItems List of items to load
	 */
	private void loadPageIcons(List<CollectionLogItem> collectionLogItems)
	{
		List<CollectionLogItem> itemsToLoad = collectionLogItems
				.stream()
				.filter(item -> !loadedCollectionLogIcons.containsKey(item.getId()))
				.collect(Collectors.toList());

		final IndexedSprite[] modIcons = client.getModIcons();

		final IndexedSprite[] newModIcons = Arrays.copyOf(modIcons, modIcons.length + itemsToLoad.size());
		int modIconIdx = modIcons.length;

		for (int i = 0; i < itemsToLoad.size(); i++)
		{
			final CollectionLogItem item = itemsToLoad.get(i);
			final ItemComposition itemComposition = itemManager.getItemComposition(item.getId());
			final BufferedImage image = ImageUtil.resizeImage(itemManager.getImage(itemComposition.getId()), 18, 16);
			final IndexedSprite sprite = ImageUtil.getImageIndexedSprite(image, client);
			final int spriteIndex = modIconIdx + i;

			newModIcons[spriteIndex] = sprite;
			loadedCollectionLogIcons.put(item.getId(), spriteIndex);
		}

		client.setModIcons(newModIcons);
	}

	/**
	 * Builds the replacement messages for the !log command with a page argument
	 *
	 * @param collectionLogPage Page to format into a chat message
	 * @return Replacement message
	 */
	private String buildMessageReplacement(CollectionLogPage collectionLogPage)
	{
		StringBuilder itemBuilder = new StringBuilder();
		int obtained = 0;
		for (CollectionLogItem item : collectionLogPage.getItems())
		{
			if (!item.isObtained())
			{
				continue;
			}
			obtained++;

			String itemString = "<img=" + loadedCollectionLogIcons.get(item.getId()) + ">";
			if (item.getQuantity() > 1)
			{
				itemString += "x" + item.getQuantity();
			}
			itemString += "  ";
			itemBuilder.append(itemString);
		}

		String replacementMessage = collectionLogPage.getName() + ": " + obtained + "/" + collectionLogPage.getItems().size() + " ";
		replacementMessage += itemBuilder.toString();

		return replacementMessage;
	}
}
