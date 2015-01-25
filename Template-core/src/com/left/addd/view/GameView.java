package com.left.addd.view;

import static com.left.addd.utils.Log.log;
import static com.left.addd.utils.Log.pCoords;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.left.addd.AdddGame;
import com.left.addd.model.GameModel;
import com.left.addd.services.SoundManager.SoundList;
import com.left.addd.view.Panner;
import com.left.addd.view.PannerDesktop;
import com.left.addd.view.PannerMobile;
import com.left.addd.model.Building;
import com.left.addd.model.Direction;
import com.left.addd.model.Entity;
import com.left.addd.model.StateChangedListener;
import com.left.addd.model.Tile;
import com.left.addd.view.GameView;
import com.left.addd.view.PannerAbstract;
import com.left.addd.view.TileImageType;

/**
 * Manages the drawing of the model to the screen and player controls. Reference:
 * https://github.com/libgdx/libgdx/tree/master/demos/very-angry-robots/very-angry-robots/src/com/badlydrawngames/veryangryrobots
 */
public class GameView implements InputProcessor, StateChangedListener {
	public static final int TILE_LENGTH = 32;

	private final AdddGame game;
	private final GameModel gameModel;
	private final TextureAtlas atlas;
	protected OrthographicCamera viewCamera;

	// Camera data
	private Panner panner;

	// I/O data
	private int buttonTouched;
	private Vector2 hoverCoordinate;
	/** hoverX and hoverY are guaranteed to be within gameModel bounds when isHovering == true */
	private boolean isHovering;
	private int hoverX;
	private int hoverY;
	private Vector2 clickCoordinate;
	/** currentTileX and currentTileY are guaranteed to be within gameModel bounds */
	private int clickX;
	private int clickY;
	private Vector2 rightClickCoordinate;
	private int rightClickX;
	private int rightClickY;

	private Vector3 tooltip;
	private Entity tooltipEntity;

	private Color hoverColor;

	private static final Color queryColor = new Color(0.8f, 0.8f, 1, 1);
	private static final Color buildColor = new Color(0.8f, 1, 0.8f, 0.8f);
	private static final Color blockColor = new Color(1, 0.7f, 0.7f, 0.6f);
	private static final Color blankColor = new Color(1, 1, 1, 1);

	// Tile rendering data
	private List<EntitySprite> entitySprites;

	// Assets
	private final Map<TileImageType, Image> tileImageCache;
	private final TileImageType[][] tileImageTypes;

	// ********************
	// *** Constructor ****
	// ********************

	public GameView(AdddGame game, GameModel model, TextureAtlas atlas) {
		this.game = game;
		this.gameModel = model;
		this.atlas = atlas;
		this.viewCamera = new OrthographicCamera();

		Vector3 pannerMin = new Vector3((-3) * TILE_LENGTH, (-3) * TILE_LENGTH, 0);
		Vector3 pannerMax = new Vector3((gameModel.width + 3) * TILE_LENGTH, (gameModel.height + 3)
				* TILE_LENGTH, 0);
		switch(Gdx.app.getType()) {
		case Applet:
		case Desktop:
		case HeadlessDesktop:
		case WebGL:
			this.panner = new PannerDesktop(atlas, pannerMin, pannerMax);
			break;
		case Android:
		case iOS:
			this.panner = new PannerMobile(atlas, pannerMin, pannerMax);
			break;
		}

		buttonTouched = Buttons.LEFT;
		hoverCoordinate = new Vector2();
		isHovering = false;
		clickCoordinate = new Vector2();
		rightClickCoordinate = new Vector2();
		tooltip = new Vector3();
		tooltipEntity = null;

		entitySprites = new ArrayList<EntitySprite>();

		// Load all the tile images into cache
		this.tileImageCache = new EnumMap<TileImageType, Image>(TileImageType.class);
		for(TileImageType type: TileImageType.values()) {
			AtlasRegion region = atlas.findRegion(type.getFileName());
			if(region != null) {
				Image image = new Image(region);
				tileImageCache.put(type, image);
			}
		}

		this.tileImageTypes = new TileImageType[gameModel.width][gameModel.height];
		updateAllTiles();
		create();
	}

	// ********************
	// **** Internals *****
	// ********************

	public GameModel getModel() {
		return gameModel;
	}

	/**
	 * Calibrates currentTileX and currentTileY's values.
	 *
	 * @param screenX Screen X coordinate from bottom left
	 * @param screenY Screen Y coordinate from bottom left
	 * @return true if currentTileX and currentTileY have been adjusted.
	 */
	private boolean setClickTileFromScreen(float screenX, float screenY) {
		Vector3 touchPoint = new Vector3();
		touchPoint.set(screenX, screenY, 0);
		panner.unproject(touchPoint);
		clickCoordinate.set(touchPoint.x / TILE_LENGTH, touchPoint.y / TILE_LENGTH);
		int x = (int) clickCoordinate.x;
		int y = (int) clickCoordinate.y;
		if(0 <= x && x < gameModel.width && 0 <= y && y < gameModel.height) {
			clickX = x;
			clickY = y;
			return true;
		}
		return false;
	}

	/**
	 * Calibrates currentTileX and currentTileY's values.
	 *
	 * @param screenX Screen X coordinate from bottom left
	 * @param screenY Screen Y coordinate from bottom left
	 * @return true if currentTileX and currentTileY have been adjusted.
	 */
	private boolean setRightClickTileFromScreen(float screenX, float screenY) {
		Vector3 touchPoint = new Vector3();
		touchPoint.set(screenX, screenY, 0);
		panner.unproject(touchPoint);
		rightClickCoordinate.set(touchPoint.x / TILE_LENGTH, touchPoint.y / TILE_LENGTH);
		int x = (int) rightClickCoordinate.x;
		int y = (int) rightClickCoordinate.y;
		if(0 <= x && x < gameModel.width && 0 <= y && y < gameModel.height) {
			rightClickX = x;
			rightClickY = y;
			return true;
		}
		return false;
	}

	/**
	 * Calibrates hoverX and hoverY values.
	 *
	 * @param screenX Screen X coordinate from bottom left
	 * @param screenY Screen Y coordinate from bottom left
	 * @return true if hoverX and hoverY have been adjusted.
	 */
	private boolean setHoverTileFromScreen(float screenX, float screenY) {
		Vector3 touchPoint = new Vector3();
		touchPoint.set(screenX, screenY, 0);
		panner.unproject(touchPoint);
		hoverCoordinate.set(touchPoint.x / TILE_LENGTH, touchPoint.y / TILE_LENGTH);
		int x = (int) hoverCoordinate.x;
		int y = (int) hoverCoordinate.y;
		if(0 <= x && x < gameModel.width && 0 <= y && y < gameModel.height) {
			hoverX = x;
			hoverY = y;
			return true;
		}
		return false;
	}

	// ********************
	// *** Camera Tools ***
	// ********************

	/**
	 * Checks for arrow keys being pressed, and pans accordingly. TODO Merge this into PannerDesktop?
	 */
	private void panKeyboard() {
		if(Gdx.input.isKeyPressed(Keys.UP)) {
			panner.pan(0, PannerAbstract.DEFAULT_PAN, 0);
		} else if(Gdx.input.isKeyPressed(Keys.DOWN)) {
			panner.pan(0, -PannerAbstract.DEFAULT_PAN, 0);
		}
		if(Gdx.input.isKeyPressed(Keys.LEFT)) {
			panner.pan(-PannerAbstract.DEFAULT_PAN, 0, 0);
		} else if(Gdx.input.isKeyPressed(Keys.RIGHT)) {
			panner.pan(PannerAbstract.DEFAULT_PAN, 0, 0);
		}
	}

	// ********************
	// * Input Processing *
	// ********************

	@Override
	public boolean keyDown(int keycode) {
		log("GameView", "KeyDown " + keycode);
		return false;
	}

	@Override
	public boolean keyUp(int keycode) {
		log("GameView", "KeyUp " + keycode);
		return false;
	}

	@Override
	public boolean keyTyped(char character) {
		log("GameView", "KeyTyped " + character + " 0x" + Integer.toHexString(character));
		return false;
	}

	@Override
	public boolean touchDown(int screenX, int screenY, int pointer, int button) {
		//log("GameView", "touchDown " + pCoords(screenX, screenY) + " pointer=" + pointer + " button=" + button);
		buttonTouched = button;
		if(panner.touchDown(screenX, screenY, pointer, button)) {
			// Panning
			return true;
		}
		return false;
	}

	@Override
	public boolean touchUp(int screenX, int screenY, int pointer, int button) {
		//log("GameView", "touchUp " + pCoords(screenX, screenY) + " pointer=" + pointer + " button=" + button);
		if(panner.touchUp(screenX, screenY, pointer, button)) {
			// Panning
			return true;
		}

		if(button == Buttons.LEFT) {
			// set the tile coordinates
			if(setClickTileFromScreen(screenX, screenY)) {
				// check if there is an entity on top
				boolean targetFound = false;
				for(EntitySprite e: entitySprites) {
					if (!targetFound && isTargetInRect(clickCoordinate.x, clickCoordinate.y, e.getX(), e.getY(), e.getWidth() / TILE_LENGTH, e.getHeight() / TILE_LENGTH)) {
						// show information and actions for the entity
						log("Target found " + pCoords(e.getX(), e.getY()));
						e.select();
						targetFound = true;
					} else {
						e.deselect();
					}
				}
			}
		} else if(button == Buttons.RIGHT) {
			// set the tile coordinates
			if (setRightClickTileFromScreen(screenX, screenY)) {
				// deselect all entities
				for(EntitySprite e: entitySprites) {
					e.deselect();
				}
			}
		}

		if(button == Buttons.RIGHT) {
			// Deselect
			return true;
		} else if(setClickTileFromScreen(screenX, screenY)) {
			// Interact with tile
			touchTile();
		}
		return true;
	}

	@Override
	public boolean touchDragged(int screenX, int screenY, int pointer) {
		// Panning
		if(panner.touchDragged(screenX, screenY, pointer, buttonTouched)) {
			return true;
		}
		// TODO draggable functions with Tiles
		// return true if meaningful action is taken.
		return false;
	}

	@Override
	public boolean mouseMoved(int screenX, int screenY) {
		isHovering = setHoverTileFromScreen(screenX, screenY);
		if(isHovering) {
			hoverTile();
		}

		tooltip.set(screenX, screenY, 0);
		panner.unproject(tooltip);

		return true;
	}

	@Override
	public boolean scrolled(int amount) {
		return false;
	}

	// ********************
	// * Tile Processing **
	// ********************

	/**
	 * Tells the GameModel to perform an action at the given Tile coordinate.
	 */
	private void touchTile() {
		game.getSound().play(SoundList.CLICK);

		// Update all appropriate tiles
		updateTile(clickX, clickY);

		// TODO insert tile clicking logic here. create functions for Tile and call them here.
		// ie. tile.interact();
	}

	/**
	 * Updates the graphics on the currently hovering tile.
	 */
	private void hoverTile() {
		hoverColor = queryColor;

		// TODO insert tile hovering logic here. create functions for Tile and call them here.
		// ie. this.showTileInfo(tile); show a popup next to the tile
	}

	/**
	 * Call this to update the Tile View for the given Tile coordinate. Note: only tile.x and tile.y are used.
	 *
	 * @param tile Coordinate of Tile to update.
	 */
	private void updateTile(int x, int y) {
		Tile tile = gameModel.getTile(x, y);
		if(tile.hasNetwork()) {
			// Update tile and NESW neighbours
			setTileImageType(tile);
			setTileImageType(tile.getNeighbour(Direction.NORTH));
			setTileImageType(tile.getNeighbour(Direction.EAST));
			setTileImageType(tile.getNeighbour(Direction.SOUTH));
			setTileImageType(tile.getNeighbour(Direction.WEST));
		} else {
			setTileImageType(tile);
		}
	}

	/**
	 * Update all the tile views.
	 */
	private void updateAllTiles() {
		for(int i = 0; i < gameModel.width; i++) {
			for(int j = 0; j < gameModel.height; j++) {
				setTileImageType(gameModel.getTile(i, j));
			}
		}
	}

	/**
	 * Updates the TileImageType for the given Tile coordinate.
	 *
	 * @param x
	 * @param y
	 */
	private void setTileImageType(Tile tile) {
		if(Tile.isDummyTile(tile)) {
			return;
		}
		tileImageTypes[tile.x][tile.y] = TileImageType.getImageFromTile(tile);
	}

	// ********************
	// **** Rendering *****
	// ********************

	public void create() {
		gameModel.addListener(this);
	}

	public void render(SpriteBatch batch, float delta) {
		// Pan camera
		panKeyboard();

		batch.setProjectionMatrix(panner.getCamera().combined);
		batch.begin();
		renderTiles(batch, delta);
		renderHover(batch, delta);
		renderEntities(batch, delta);
		renderTooltip(batch);
		batch.end();

		panner.render(delta);

		// Draw debug stuff (gridlines?)
	}

	private void renderTiles(SpriteBatch batch, float delta) {
		for(int i = 0; i < gameModel.width; i++) {
			for(int j = 0; j < gameModel.height; j++) {
				Image image = tileImageCache.get(tileImageTypes[i][j]);
				if(image != null) {
					image.setPosition(i * GameView.TILE_LENGTH, j * GameView.TILE_LENGTH);
					image.draw(batch, 1f);
				}
			}
		}
	}

	private void renderHover(SpriteBatch batch, float delta) {
		if(isHovering) {
			Image image;
			Tile tile = gameModel.getTile(hoverX, hoverY);
			int x = tile.x;
			int y = tile.y;
			image = tileImageCache.get(tileImageTypes[x][y]);
			if(image != null) {
				image.setPosition(x * GameView.TILE_LENGTH, y * GameView.TILE_LENGTH);
				image.setColor(hoverColor);
				image.draw(batch, 1f);
				image.setColor(blankColor);
			}
		}
	}

	private void renderEntities(SpriteBatch batch, float delta) {
		for (EntitySprite es : entitySprites) {
			es.getImageForRender(delta).draw(batch, 1f);
		}
	}

	private void renderTooltip(SpriteBatch batch) {
		BitmapFont font = new BitmapFont();
		font.setColor(Color.GREEN);
		float tooltipOffset = 10;
		float lineHeight = 32;
		Entity te = null;
		for (EntitySprite es: entitySprites) {
			//System.out.println("es "+es.getX()*TILE_LENGTH+","+es.getY()*TILE_LENGTH+" "+es.getWidth()+"x"+es.getHeight());
			if((es.getX()*TILE_LENGTH < tooltip.x && (es.getX()*TILE_LENGTH + es.getWidth()) > tooltip.x) &&
			   (es.getY()*TILE_LENGTH < tooltip.y && (es.getY()*TILE_LENGTH + es.getHeight()) > tooltip.y)) {
				te = es.getEntity();
				break;
			}
		}
		if (te != null) {
			Set<String> keys = te.getMetadata().keySet();
			int lineCount = 0;
			for (String s : keys) {
				String metadata = s + ": "+te.getMetadata().get(s).toString();
				font.draw(batch, metadata , tooltip.x+tooltipOffset, tooltip.y+tooltipOffset+lineHeight*lineCount);
				lineCount++;
			}
		}
	}

	public void resize(int width, int height) {
		panner.resize(width, height);

		// width & height are already scaled.
		viewCamera.viewportWidth = width;
		viewCamera.viewportHeight = height;
		viewCamera.position.set(0, 0, 0);
		viewCamera.update();
	}

	public void OnStateChanged() {
		log("GameView", "GameModel state changed");
		final List<Entity> entities = gameModel.getEntities();
		if(entities.size() != entitySprites.size()) {
			// regenerate view
			entitySprites.clear();
			for(int i = 0; i < entities.size(); i++) {
				EntitySprite entitySprite = new EntitySprite(atlas, entities.get(i));
				entitySprite.create();
				entitySprites.add(entitySprite);
			}
		}
	}

	private static boolean isTargetInRect(float targetX, float targetY, float x, float y, float width, float height) {
		log ("Target " + pCoords(targetX, targetY) + " Rect " + pCoords(x, y) + " " + pCoords(x+width, y+height));

		return (targetX >= x && targetY >= y && targetX < x + width && targetY < y + height);
	}
}
