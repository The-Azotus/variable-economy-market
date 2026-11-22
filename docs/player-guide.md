# Player guide

The market is an add-on for Variable Economy: a market run by the server. You can sell the market items for money and buy items from what everyone has sold. Everything is paid through your bank account, so you don't need coins in your inventory.

Your bank account, coins and the Bank block are part of Variable Economy's core. Money is counted in **bits**, and the market pays into and takes from the account you're using for trades (your **active account**). [Core's player guide](https://github.com/The-Azotus/variable-economy-core/blob/main/docs/player-guide.md) covers the bank, coins, accounts and `/pay`.

## The Market Terminal

The Market Terminal is where you buy from and sell to the server's market. Right-click one to open its screen, or type `/market` if your server allows it. If you see the message *"The market is only available at a Market Terminal on this server"*, find the block instead. If you walk more than about 8 blocks away from the terminal you opened, its screen closes.

The screen fills most of the window, and the list shows as many items as fit. On a wide window the selected item's panel is to the right of the list; on a narrow one it's below it. The [Selling box](#the-selling-box) and your inventory run along the bottom.

| Part | What it shows |
| --- | --- |
| **Bank** (top right) | The balance of the account you're using, in bits. On a narrow screen it sits at the end of the search row and large amounts are shortened, such as **123k**. |
| **Items / My trades** (title row) | Switches the list between the market's items and your own recent trades (see [My trades](#my-trades)). On a small window it also switches to the **Sell box**. |
| **Search box** | Type part of an item's name to filter the list. **`@create`** shows only one mod's items (by its name or ID, so `@farmer` finds Farmer's Delight). **`#ingots`** shows items with a matching tag; `#c:ingots` matches that tag's full ID. Combine words to narrow it down: `@create #plates`, or `iron ingot` (both words must match). A mod ID followed by `:`, such as `create:`, still works too. |
| **Sort button** | Click to cycle the order: **Name**, **Cheapest** (lowest buy price first), **Best pay** (highest sell price first), **Stock** (most first). |
| **Filter button** | Click to cycle what's shown: **All** (every item in stock), **Buyable** (in stock and for sale), **Sellable** (the market buys it), **Held** (you have some and the market buys it). Sold-out items the market buys still appear under **Sellable** and **Held**, so you can sell them. |
| **Stock** | How many the market has. **∞** means it never runs out. **0** in red (under **Sellable** and **Held**) means it's sold out. |
| **Buy** | What the market charges you for one. Cheap items show decimals, such as **0.99**. **–** means the market doesn't sell this item. |
| **Sell** | What the market pays you for one. **–** means the market doesn't buy this item. |
| **Have** | How many you could sell right now. |
| **▲ / ▼** (last column) | The price is above (▲) or below (▼) normal. Hover over the row for the exact percentage. |

The sort and filter you pick stay the same the next time you open the market, until you quit the game.

Click an item to select it. Set a quantity with the box and the **−** / **+** buttons; hold Shift to change it by 16. Or use:

- **Max**: sets the quantity to the most you can buy in one order. It counts your bank balance, the market's stock, the room in your inventory and the order limit, and allows for the price rising as you buy.
- **All**: sets the quantity to every copy you hold, up to the order limit.

These only fill in the quantity; you still click **Buy** or **Sell**. The text beside them says what limited the amount, for example *"All you can afford"* or *"640 of 1,000 (limit)"*. Otherwise it shows the order limit, and turns red if the quantity is over it.

The **Buy** and **Sell** buttons show the total for that quantity; hover over one for the average price per item. They're greyed out if you can't afford the order, don't hold enough, don't have room, or the market is out of stock. You never get a worse deal than the button shows: if the price moves in your favour just as you click, the trade goes through at the better price; if it moves against you, nothing happens and the button shows the new total.

Next to the selected item, a small line shows its price over the last hour or so; the dotted line is the normal price. Hover over it for the low, the high and the current price. On a wide screen the panel also lists the item's buy and sell price, stock, how many you have, and its price as a percentage of normal (**Vs. base**).

The line along the bottom of the screen tells you how your last trade went.

### The Selling box

The Selling box sells many different items at once. It sits along the bottom of the screen with your inventory beside it. On a small window it takes the list's place instead: click the title-row button until it says **Sell box**.

- **Click** a stack in your inventory to put all of it in the box. **Right-click** puts in one. **Shift-click** puts in every copy of that item you hold.
- **Click** an item in the box to take it out again, or **right-click** to take one out. **Clear** empties the box.
- Items the market doesn't buy from you are dimmed and can't go in: items it doesn't buy, enchanted, damaged or renamed copies, and coins. Hover over one to see why.
- The box holds one slot per item, up to the order limit (640 by default) of each.
- The **Sell** button under the box shows what everything in it is worth right now. Click it to sell it all.

Until you click **Sell**, the items stay in your inventory: the box only marks them. Closing the screen just empties the box, and nothing is ever lost.

Each item in the box is sold on its own at its current price, as long as that pays at least what the box showed. An item whose price dropped just as you clicked, or that you no longer have, stays in the box, marked in red; hover over it to see why, and click **Sell** again to sell it at the new price. The line at the bottom of the screen says how many items sold and for how much.

### My trades

Click **Items** in the title row to switch to **My trades**: your own last 50 trades, newest first, with what you bought or sold, how many, the total, and how long ago. Click a trade to go back to the item list with that item selected. Click **My trades** to switch back. Only you see your trades.

### Trading by command

Where the server allows commands, `/market sell <item> <amount>` and `/market buy <item> <amount>` trade without the screen, at whatever the price is when the command runs. `/market stock` lists the items the market has in stock (items with unlimited stock aren't listed, since they never run out), `/market stock <item>` shows one item's stock, and `/market price <item>` an item's prices.

## Selling from anywhere: tooltips

Hover over any item, in your inventory, a chest or a recipe viewer, to see what the market pays for it:

> Value: 32 bits (636 for 20)

The first number is what one is worth; the one in brackets is the whole stack. A single item just shows *"Value: 32 bits"*. Cheap items show decimals, such as *"Value: 0.99 bits"*. Selling a whole stack still pays the exact total, rounded once. The line shows even when the market is sold out of the item, since what it pays doesn't depend on its stock.

If an item has no such line, the market doesn't buy it. To turn the line off, set `show_sell_price_in_tooltips = false` in `config/variable_economy_market-client.toml`.

## How prices move

- **Selling lowers the price** and **buying raises it**, a little with every item. A big order slides the price as it goes, so 640 items sell for less than 640 × the price shown for one.
- Splitting an order into smaller ones never gets you a better total than one big order.
- **Prices recover.** Every minute or so, each price moves part of the way back toward normal.
- Buying something and selling it straight back always loses a little money, so there's no free profit.

## What the market accepts

- **Only plain items.** Enchanted, damaged or renamed copies don't count. If you hold 10 iron ingots and 5 of them are renamed, you can sell 5. Some mod data is let through by the server (relics' levels, for example); it's lost when you sell, and what you buy always comes fresh.
- **Only your main inventory** (hotbar and backpack rows). Armour, offhand and modded slots aren't checked.
- **Only orders that fit.** If you buy more than fits in your inventory, the purchase is refused. Nothing is ever dropped on the ground.
- **Only items you've unlocked.** On servers with ProgressiveStages, items locked for you don't appear in the market until you reach their stage. Prices are the same for everyone.
- **Never coins.** The market doesn't buy or sell coins; they go in and out of your account at a Bank.

## Messages

| Message | Meaning |
| --- | --- |
| *Bought N × item for X* / *Sold N × item for X* | Done. |
| *Price changed from X to Y. Click again to confirm* | Someone else traded, or prices recovered, just as you clicked, and the new price is worse for you. Nothing was bought or sold. The button now shows the new total. |
| *Sold N items for X* | The Selling box sold everything in it. If some items stayed in the box, the message says how many: hover over them to see why. |
| *You don't have that many (enchanted, damaged or renamed copies don't count)* | You hold fewer plain copies than you tried to sell. |
| *The market doesn't have that many in stock* | Try a smaller quantity. |
| *Your bank account can't cover that* | Not enough money for this order. |
| *Not enough room in your inventory* | Make space and try again. |
| *The market does not buy this item* / *The market does not sell this item* | That direction is switched off for this item. |
| *That item is not on the market* | It was taken off the market while you had the screen open. |
| *That order is too large* | Orders are capped (640 by default). Split it up. |
| *Enter a quantity of at least 1* | The quantity box is empty or 0. |
| *You can't use that bank account for this* | Your active account is a shared one you may only deposit into, so you can sell but not buy. Switch accounts at a Bank. |
| *That bank account doesn't exist* | The account was deleted just as you traded. Try again: you're back on your personal account. |
| *The bank is unavailable right now* | The server's bank can't be reached. Tell an admin. |
| *The market is only available at a Market Terminal on this server* | The server keeps the market to its block. |

## Crafting the Market Terminal

The Market Terminal is 8 Bits around a crafting table:

```
Bit  Bit            Bit
Bit  Crafting table Bit
Bit  Bit            Bit
```

Your server may use a different recipe; check a recipe viewer if this doesn't work. The terminal faces you when you place it. Break it by hand to pick it up again; it breaks faster with an axe.
