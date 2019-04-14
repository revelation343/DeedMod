package com.revelation.wurm;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

import com.wurmonline.server.LoginHandler;
import com.wurmonline.server.NoSuchPlayerException;
import com.wurmonline.server.Players;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.players.PlayerInfo;
import com.wurmonline.server.players.PlayerInfoFactory;
import com.wurmonline.server.villages.Villages;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.questions.VillageFoundationQuestion;


public class DeedMod implements WurmServerMod, Configurable, ServerStartedListener {

	private Integer settlementsPerSteamID = -1;
	private Integer maxDeedTiles = -1;
	private Integer maxDeedX = -1;
	private Integer maxDeedY = -1;
	private Integer powerOverride = 1;
	private Integer totalGuards = 1;
	private Logger logger = Logger.getLogger(this.getClass().getName());

	@Override
	public void onServerStarted() {
		logger.log(Level.INFO, "Initializing Deed modifications");
	}

	@Override
	public void configure(Properties properties) {
		settlementsPerSteamID = Integer.parseInt(properties.getProperty("settlementsPerSteamID", Integer.toString(settlementsPerSteamID)));
		maxDeedTiles = Integer.parseInt(properties.getProperty("maxDeedTiles", Integer.toString(maxDeedTiles)));
		maxDeedX = Integer.parseInt(properties.getProperty("maxDeedX", Integer.toString(maxDeedX)));
		maxDeedY = Integer.parseInt(properties.getProperty("maxDeedY", Integer.toString(maxDeedY)));
		powerOverride = Integer.parseInt(properties.getProperty("powerOverride", Integer.toString(powerOverride)));
		totalGuards = Integer.parseInt (properties.getProperty ("totalGuards", Integer.toString(totalGuards)));

		logger.log(Level.INFO, "powerOverride: " + powerOverride);
		logger.log(Level.INFO, "settlementsPerSteamID: " + settlementsPerSteamID);
		logger.log(Level.INFO, "maxDeedTiles: " + maxDeedTiles);
		logger.log(Level.INFO, "maxDeedX: " + maxDeedX);
		logger.log(Level.INFO, "maxDeedY: " + maxDeedY);
		logger.log(Level.INFO, "totalGuards: " + totalGuards);


		HookManager.getInstance().registerHook("com.wurmonline.server.questions.VillageFoundationQuestion", "sendIntro", "()V", new InvocationHandlerFactory() {

			@Override
			public InvocationHandler createInvocationHandler() {
				return new InvocationHandler() {


					@Override
					public Object invoke(Object object, Method method, Object[] args) throws Throwable {
						VillageFoundationQuestion vq = (VillageFoundationQuestion) object;
						Creature c = vq.getResponder();

						if (!c.isPlayer()) {
							return null;
						}

						Player resp = (Player) c;
						String respSteamID = String.valueOf(resp.getSteamId());

						int deedCount = 0;

						if (settlementsPerSteamID == 0 && c.getPower() < powerOverride) {
							c.getCommunicator().sendNormalServerMessage("Sorry, player settlements have been disabled on this server.");
							return null;
						}

						if (settlementsPerSteamID > -1 && c.getPower() < powerOverride) {
							for (Village v : Villages.getVillages()) {
								try {
									Player p = Players.getInstance().getPlayer(v.mayorName);

									//logger.log(Level.INFO, "Checking village: " + v.getName() + " mayor: " + p.getName());

									if (p.getPower() < powerOverride) { // This change should ignore deeds with mayors who are GMs at or above powerOverride on the same steam id.
										if (p.getSteamId() == resp.getSteamId()) {
											//logger.log(Level.INFO, "deedCount++");
											deedCount++;
										}
									}
								} catch (NoSuchPlayerException nsp) {
									String hashCheck = LoginHandler.hashPassword(respSteamID, LoginHandler.encrypt(LoginHandler.raiseFirstLetter(v.mayorName)));
									PlayerInfo file = PlayerInfoFactory.createPlayerInfo(v.mayorName);
									//logger.log(Level.INFO, "Exception caught >> " + v.mayorName +" << (" + file.getPower() + ")");

									if (hashCheck.equals(file.getPassword()) && file.getPower() < powerOverride ) {
										//logger.log(Level.INFO, "deedCount++");
										deedCount++;
									}
								}
								if (deedCount >= settlementsPerSteamID) {
									c.getCommunicator().sendAlertServerMessage("Sorry, only " + settlementsPerSteamID + " villages per your SteamID '" + respSteamID + "'");
									return null;
								}
							}
						}
						return method.invoke(object, args);
					}
				};
			}
		});

		HookManager.getInstance().registerHook("com.wurmonline.server.questions.VillageFoundationQuestion", "setSize", "()V", new InvocationHandlerFactory() {


			@Override
			public InvocationHandler createInvocationHandler() {
				return new InvocationHandler() {

					@Override
					public Object invoke(Object object, Method method, Object[] args) throws Throwable {
						VillageFoundationQuestion vq = (VillageFoundationQuestion) object;
						Creature c = vq.getResponder();
						int diameterX = (vq.selectedWest + vq.selectedEast + 1);
						if (maxDeedX > -1 && diameterX > maxDeedX && c.getPower() < powerOverride) {
							vq.selectedEast = (maxDeedX - 1) / 2;
							vq.selectedWest = (maxDeedX - 1) / 2;
							if (vq.selectedEast < 5) { vq.selectedEast = 5; } // Sanity check. During testing I was getting values at less than 5.
							if (vq.selectedWest < 5) { vq.selectedWest = 5; } // ditto
							c.getCommunicator().sendAlertServerMessage("Max total east/west size is: " + maxDeedX);
							c.getCommunicator().sendNormalServerMessage("Setting east and west sizes to: " + (maxDeedX / 2));
						}
						int diameterY = (vq.selectedNorth + vq.selectedSouth + 1);
						if (maxDeedY > -1 && diameterY > maxDeedY && c.getPower() < powerOverride) {
							vq.selectedNorth = (maxDeedY - 1) / 2;
							vq.selectedSouth = (maxDeedY - 1) / 2;
							if (vq.selectedNorth < 5) { vq.selectedNorth = 5; } // Sanity check. During testing I was getting values at less than 5.
							if (vq.selectedSouth < 5) { vq.selectedSouth = 5; } // ditto
							c.getCommunicator().sendAlertServerMessage("Max total north/south size is: " + maxDeedY);
							c.getCommunicator().sendNormalServerMessage("Setting north and south sizes to: " + (maxDeedY / 2));
						}
						if (maxDeedTiles > -1 && (diameterX * diameterY) > maxDeedTiles && c.getPower() < powerOverride) {
							c.getCommunicator().sendAlertServerMessage("Max total tiles is: " + maxDeedTiles);
							int maxEW = ((maxDeedTiles / 2) - 1 < maxDeedX) ? ((maxDeedTiles / 2) - 1) / 2 : (maxDeedX - 1) / 2;
							int maxNS = ((maxDeedTiles / 2) - 1 < maxDeedY) ? ((maxDeedTiles / 2) - 1) / 2 : (maxDeedY - 1) / 2;
							if (maxEW < 5) { maxEW = 5; } // Sanity check. During testing I was getting values at less than 5.
							vq.selectedEast = vq.selectedWest = maxEW;
							c.getCommunicator().sendNormalServerMessage("Setting east and west sizes to: " + maxEW);
							if (maxNS < 5) { maxNS = 5; } // ditto
							vq.selectedNorth = vq.selectedSouth = maxNS;
							c.getCommunicator().sendNormalServerMessage("Setting north and south sizes to: " + maxNS);
						}
						return method.invoke(object, args);
					}
				};
			}
		});

		HookManager.getInstance().registerHook("com.wurmonline.server.questions.VillageFoundationQuestion", "addGuardCost", "(Ljava/lang/StringBuilder;)V", new InvocationHandlerFactory() {


			public InvocationHandler createInvocationHandler() {
				return new InvocationHandler() {

					@Override
					public Object invoke(Object object, Method method, Object[] args) throws Throwable {
						VillageFoundationQuestion vq = (VillageFoundationQuestion) object;
						Creature c = vq.getResponder();
						if (totalGuards > -1 && c.getPower() < powerOverride){
							if (vq.selectedGuards > totalGuards) {
								vq.selectedGuards = totalGuards;
								c.getCommunicator().sendAlertServerMessage("Setting guard count to the maximum " + totalGuards + " guards allowed.");
							}
						}
						return method.invoke(object, args);
					}
				};
			}
		});
	}
}
