package assign11;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Collections;

import javax.swing.JPanel;

/**
 * A TrackEditor is the interactive GUI component for drawing a sequence of note
 * events or volume changes in a track.
 * 
 * @author CS 1420 course staff and Your Name
 * @version November 20, 2025
 */
public class TrackEditor extends JPanel implements MouseListener, MouseMotionListener {

    public static enum Mode {
        NOTE, VOLUME, COPY
    };

    private Mode mode;

    private int trackNumber;
    private SimpleSynthesizer synth;
    private ArrayList<AudioEvent> events; // The AudioEvents for this track
    private ArrayList<NoteEvent> notesToCopy; // Stores notes during a copy operation

    private int columns, rows; // The number of columns and rows in the grid
    private boolean drawing; // Set to true during drawing operations
    private int currentRow, currentColumn; // Used by drawing operations
    private int noteStartColumn; // Leftmost column of the note being drawn
    private int noteDuration; // The duration of a note being drawn

    // For defining an area of the grid to copy
    private int copyFromRow, copyFromColumn; // Top and left of area
    private int copyToRow, copyToColumn; // Bottom and right of area

    // This pitch range matches a piano. You can change these values if desired.
    private static final int lowestPitch = 21;
    private static final int highestPitch = 108;

    /**
     * Create a new TrackEditor with the default configuration.
     * 
     * @param trackNumber assigned to this track in the midi system
     * @param synthesizer for making sounds
     * @param sequencer   for sequencing note events
     */
    public TrackEditor(int trackNumber, int songLength, ArrayList<AudioEvent> events, SimpleSynthesizer synth) {
        columns = songLength;
        rows = highestPitch - lowestPitch + 1;

        this.trackNumber = trackNumber;
        this.synth = synth;
        this.events = events;
        notesToCopy = new ArrayList<NoteEvent>();
        drawing = false;
        mode = Mode.NOTE;

        setBackground(Color.WHITE);

        addMouseListener(this);
        addMouseMotionListener(this);
    }

    /**
     * Removes all events from the track.
     */
    public void clearTrack() {
        events.clear();
        repaint();
    }

    /**
     * Set the song duration in ticks.
     * 
     * @param songLength in ticks
     */
    public void setSongLength(int songLength) {
        columns = songLength;
        if (columns < 1)
            columns = 1;
        repaint();
    }

    /**
     * Set the editor to the specified mode.
     * 
     * @param mode - either Mode.NOTE, Mode.VOLUME, or Mode.COPY
     */
    public void setMode(Mode mode) {
        this.mode = mode;
        // Set volume back to default in case it was changed in volume mode
        synth.setVolume(trackNumber, 100);
    }

    /**
     * This method is called by the system when a component needs to be painted.
     * Which can be at one of three times: --when the component first appears --when
     * the size of the component changes (including resizing by the user) --when
     * repaint() is called
     * 
     * Partially overrides the paintComponent method of JPanel.
     * 
     * @param g -- graphics context onto which we can draw
     */
    public void paintComponent(Graphics g) {
        // Call superclass JPanel's paintComponent method to fill in panel with
        // background color.
        super.paintComponent(g);

        /*
         * Draw the volume bars These should be a very light color so that other colors
         * can be easily seen. To make a light color, set red, green, and blue
         * components to all be between 230 and 255. The volume stays constant between
         * volume events, so follow the procedure described here.
         */
        int previousVolume = 100; // the volume begins with value 100 by default
        int previousTime = 0; // the beginning of the song

        // Set a very light color for volume bars
        g.setColor(new Color(240, 240, 250));

        for (AudioEvent event : events) {
            if (event instanceof VolumeEvent) {
                // Draw a rectangle showing the volume prior to this event
                int leftX = colToPixel(previousTime);
                int topY = rowToPixel(volumeToRow(previousVolume));
                int rightX = colToPixel(event.getTime());
                int bottomY = getHeight();

                g.fillRect(leftX, topY, rightX - leftX, bottomY - topY);

                // Update previousVolume and previousTime with values from this event.
                previousVolume = ((VolumeEvent) event).getValue();
                previousTime = event.getTime();
            }
        }

        // Draw one more rectangle for the last volume event.
        int leftX = colToPixel(previousTime);
        int topY = rowToPixel(volumeToRow(previousVolume));
        int rightX = getWidth();
        int bottomY = getHeight();
        g.fillRect(leftX, topY, rightX - leftX, bottomY - topY);

        // Draw the grid using a different, darker color (black is a good choice for
        // this)
        g.setColor(Color.BLACK);

        // Draw horizontal lines for each row
        for (int row = 0; row <= rows; row++) {
            int y = rowToPixel(row);
            g.drawLine(0, y, getWidth(), y);
        }

        // Draw vertical lines for each column
        for (int col = 0; col <= columns; col++) {
            int x = colToPixel(col);
            g.drawLine(x, 0, x, getHeight());
        }

        // Draw some thicker lines every few rows to make it easier to use.
        // 12 rows represents one octave, so that is a natural choice for spacing.
        for (int row = 0; row <= rows; row += 12) {
            int y = rowToPixel(row);
            g.fillRect(0, y, getWidth(), 2);
        }

        // Draw some thicker lines every few columns to make it easier to use.
        for (int col = 0; col <= columns; col += 4) {
            int x = colToPixel(col);
            g.fillRect(x, 0, 2, getHeight());
        }

        // Draw preview only if something is currently being drawn by the mouse
        if (drawing) {
            if (mode == Mode.VOLUME) {
                // Set the color to volume drawing color and draw a rectangle in
                // the current column from the current row to the bottom of the panel.
                g.setColor(new Color(255, 200, 200));
                int x = colToPixel(currentColumn);
                int y = rowToPixel(currentRow);
                int width = colToPixel(currentColumn + 1) - x;
                int height = getHeight() - y;
                g.fillRect(x, y, width, height);
            } else if (mode == Mode.NOTE && noteDuration > 0) {
                // Set the color to a new color for drawing notes.
                g.setColor(new Color(100, 150, 255));
                int x = colToPixel(noteStartColumn);
                int y = rowToPixel(currentRow);
                int width = colToPixel(noteStartColumn + noteDuration) - x;
                int height = rowToPixel(currentRow + 1) - y;
                g.fillRect(x, y, width, height);
            } else if (mode == Mode.COPY) {
                // Set the color to a new color for copy selection with transparency.
                g.setColor(new Color(255, 255, 0, 100));

                int startCol = Math.min(copyFromColumn, copyToColumn);
                int endCol = Math.max(copyFromColumn, copyToColumn);
                int startRow = Math.min(copyFromRow, copyToRow);
                int endRow = Math.max(copyFromRow, copyToRow);

                int x = colToPixel(startCol);
                int y = rowToPixel(startRow);
                int width = colToPixel(endCol + 1) - x;
                int height = rowToPixel(endRow + 1) - y;
                g.fillRect(x, y, width, height);
            }
        }

        // Draw note events
        g.setColor(new Color(100, 150, 255));

        for (AudioEvent event : events) {
            if (event instanceof NoteEvent) {
                NoteEvent note = (NoteEvent) event;
                // Draw a rectangle for this note.
                int row = pitchToRow(note.getPitch());
                int x = colToPixel(note.getTime());
                int y = rowToPixel(row);
                int width = colToPixel(note.getTime() + note.getDuration()) - x;
                int height = rowToPixel(row + 1) - y;
                g.fillRect(x, y, width, height);
            }
        }
    } // end of paintComponent

    /**
     * Called when a mouse button is pressed.
     * 
     * @param e the mouse event
     */
    @Override
    public void mousePressed(MouseEvent e) {
        // Only handle left mouse button
        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }

        drawing = true;
        currentRow = pixelToRow(e.getY());
        currentColumn = pixelToCol(e.getX());
        noteStartColumn = currentColumn;

        if (mode == Mode.NOTE) {
            // Turn on this note in the synthesizer
            int pitch = rowToPitch(currentRow);
            synth.noteOn(trackNumber, pitch);
            noteDuration = 1;
        } else if (mode == Mode.VOLUME) {
            // Set the volume and play pitch 60
            int volume = rowToVolume(currentRow);
            synth.setVolume(trackNumber, volume);
            synth.noteOn(trackNumber, 60);
        } else if (mode == Mode.COPY) {
            // Start highlighting a new area to copy
            copyFromRow = currentRow;
            copyFromColumn = currentColumn;
            copyToRow = currentRow;
            copyToColumn = currentColumn;
        }

        repaint();
    }

    /**
     * Called when a mouse button is released.
     * 
     * @param e the mouse event
     */
    @Override
    public void mouseReleased(MouseEvent e) {
        if (!drawing) {
            return;
        }

        if (mode == Mode.NOTE) {
            // Turn off the note
            int pitch = rowToPitch(currentRow);
            synth.noteOff(trackNumber, pitch);

            // Recalculate duration based on current mouse position
            int releaseColumn = pixelToCol(e.getX());
            int startColumn = Math.min(currentColumn, releaseColumn);
            noteDuration = Math.abs(releaseColumn - currentColumn) + 1;

            // Create a new note event if duration is positive
            if (noteDuration > 0) {
                NoteEvent note = new NoteEvent(startColumn, trackNumber, rowToPitch(currentRow), noteDuration);
                events.add(note);
                Collections.sort(events);
            }
        } else if (mode == Mode.VOLUME) {
            // Turn off pitch 60
            synth.noteOff(trackNumber, 60);

            // Create a new volume event
            VolumeEvent volumeEvent = new VolumeEvent(currentColumn, trackNumber, rowToVolume(currentRow));
            events.add(volumeEvent);
            Collections.sort(events);
        } else if (mode == Mode.COPY) {
            // Add all note events in the copied area to notesToCopy
            notesToCopy.clear();

            int startCol = Math.min(copyFromColumn, copyToColumn);
            int endCol = Math.max(copyFromColumn, copyToColumn);
            int startRow = Math.min(copyFromRow, copyToRow);
            int endRow = Math.max(copyFromRow, copyToRow);

            for (AudioEvent event : events) {
                if (event instanceof NoteEvent) {
                    NoteEvent note = (NoteEvent) event;
                    int row = pitchToRow(note.getPitch());

                    if (note.getTime() >= startCol && note.getTime() <= endCol && row >= startRow && row <= endRow) {
                        notesToCopy.add(note);
                    }
                }
            }
        }

        drawing = false;
        repaint();
    }

    /**
     * Called when the mouse is dragged (moved while button is pressed).
     * 
     * @param e the mouse event
     */
    @Override
    public void mouseDragged(MouseEvent e) {
        if (!drawing) {
            return;
        }

        int row = pixelToRow(e.getY());
        int col = pixelToCol(e.getX());

        if (mode == Mode.NOTE) {
            // Update note duration and starting column allowing drags in either direction
            noteStartColumn = Math.min(currentColumn, col);
            noteDuration = Math.abs(col - currentColumn) + 1;

            // If row changed, update the pitch
            if (row != currentRow) {
                synth.noteOff(trackNumber, rowToPitch(currentRow));
                synth.noteOn(trackNumber, rowToPitch(row));
                currentRow = row;
            }
        } else if (mode == Mode.VOLUME) {
            // If row changed, update the volume
            if (row != currentRow) {
                int volume = rowToVolume(row);
                synth.setVolume(trackNumber, volume);
                synth.noteOn(trackNumber, 60);
                currentRow = row;
            }
        } else if (mode == Mode.COPY) {
            // Update the copy area
            copyToRow = row;
            copyToColumn = col;
        }

        repaint();
    }

    /**
     * Called when a mouse button is clicked (pressed and released).
     * 
     * @param e the mouse event
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        // Only handle non-left button clicks (right-click or other buttons)
        if (e.getButton() == MouseEvent.BUTTON1) {
            return;
        }

        int row = pixelToRow(e.getY());
        int col = pixelToCol(e.getX());

        if (mode == Mode.NOTE) {
            // Delete the first note event at this location
            int pitch = rowToPitch(row);
            for (int i = 0; i < events.size(); i++) {
                AudioEvent event = events.get(i);
                if (event instanceof NoteEvent) {
                    NoteEvent note = (NoteEvent) event;
                    // Check if this note starts at the clicked column and has the clicked pitch
                    if (note.getTime() == col && note.getPitch() == pitch) {
                        events.remove(i);
                        break;
                    }
                }
            }
            repaint();
        } else if (mode == Mode.VOLUME) {
            // Do nothing in volume mode for right-clicks
        } else if (mode == Mode.COPY) {
            // Paste all events in notesToCopy with an offset
            int timeOffset = col - copyFromColumn;
            int pitchOffset = rowToPitch(copyFromRow) - rowToPitch(row);

            for (NoteEvent original : notesToCopy) {
                int newTime = original.getTime() + timeOffset;
                int newPitch = original.getPitch() - pitchOffset;
                int newDuration = original.getDuration();

                NoteEvent newNote = new NoteEvent(newTime, trackNumber, newPitch, newDuration);
                events.add(newNote);
            }

            Collections.sort(events);
            repaint();
        }
    }

    /**
     * Not used but required by MouseListener interface.
     */
    @Override
    public void mouseEntered(MouseEvent e) {
    }

    /**
     * Not used but required by MouseListener interface.
     */
    @Override
    public void mouseExited(MouseEvent e) {
    }

    /**
     * Not used but required by MouseMotionListener interface.
     */
    @Override
    public void mouseMoved(MouseEvent e) {
    }

    //////////////////////////////////////////////////////////////////////
    // Private helper methods
    //////////////////////////////////////////////////////////////////////

    /**
     * Convert a row index in the grid to a pitch number.
     * 
     * @param rowNumber - to convert
     * @return pitch corresponding to that row
     */
    private int rowToPitch(int rowNumber) {
        return highestPitch - rowNumber;
    }

    /**
     * Convert a pitch number to a row index in the grid.
     * 
     * @param pitch - to convert
     * @return row index corresponding to that pitch
     */
    private int pitchToRow(int pitch) {
        return highestPitch - pitch;
    }

    /**
     * Convert a row index in the grid to a volume value.
     * 
     * @param rowNumber - to convert
     * @return volume value corresponding to that row
     */
    private int rowToVolume(int rowNumber) {
        return 127 - rowNumber * 127 / rows;
    }

    /**
     * Convert a volume value to a row index in the grid.
     * 
     * @param volume - to convert
     * @return row index corresponding to that volume
     */
    private int volumeToRow(int volume) {
        return rows - volume * rows / 127;
    }

    /**
     * Converts a row index to pixel y value of the TOP edge of the row.
     * 
     * @param row - index
     * @return pixel y value of the top edge
     */
    private int rowToPixel(int row) {
        return row * getHeight() / rows;
    }

    /**
     * Converts a column index to pixel x value of the LEFT edge of the column.
     * 
     * @param col - column index
     * @return pixel x value of the left side
     */
    private int colToPixel(int col) {
        return col * getWidth() / columns;
    }

    /**
     * Converts a pixel y value to a row index.
     * 
     * @param pixelY - pixel y value
     * @return index of row containing that pixel
     */
    private int pixelToRow(int pixelY) {
        return rows * pixelY / getHeight();
    }

    /**
     * Converts a pixel x value to a column index.
     * 
     * @param pixelX - pixel x value
     * @return index of column containing that pixel
     */
    private int pixelToCol(int pixelX) {
        return columns * pixelX / getWidth();
    }

    // Required by a serializable class (ignore for now)
    private static final long serialVersionUID = 1L;
}
