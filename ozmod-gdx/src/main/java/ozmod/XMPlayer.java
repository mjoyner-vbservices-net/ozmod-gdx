/*
OZMod - Java Sound Library
Copyright (C) 2012 by Igor Kravtchenko

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

Contact the author: igor@tsarevitch.org
 */

package ozmod;

import java.nio.charset.Charset;

import ozmod.SeekableBytes.Endian;

/**
 * A Class to replay XM file.
 */
public class XMPlayer extends OZModPlayer {

	protected static class Column {
		NoteInfo notesInfo[];
	}

	protected static class Instru {

		int endPanLoop;
		int endVolLoop;
		int envPan[] = new int[24];
		int envVol[] = new int[24];
		int fadeOut;
		byte name[] = new byte[22];
		int nbPointsPan;
		int nbPointsVol;
		int nbSamples;
		int panType;
		int reserved;
		Sample samples[];
		int sampleTable[] = new int[96];
		int startPanLoop;
		int startVolLoop;
		int sustainPointPan;
		int sustainPointVol;
		int type;
		int vibratoProf;
		int vibratoSpeed;
		int vibratoSweep;
		int vibratoType;

		int volType;
	}

	protected static class NoteInfo {
		int colum;
		int effect;
		int effectOperand;
		int iInstru = -1;
		int note;
	}

	protected static class Pattern {
		Column columns[];
		int nbLines;
	};

	protected static class Sample {

		AudioData audioData = new AudioData();
		int fineTune;
		int len;
		int lengthLoop;
		byte name[] = new byte[22];
		int offStop;
		int panning;
		byte relativeNote;
		int startLoop;
		int type;

		int volume;
	};

	protected class Voice {

		Instru actuInstru_;

		int actuNumInstru_ = -1;
		int arpeggioCount_, arp1_, arp2_;
		boolean bGotArpeggio_;
		boolean bGotRetrigNote_;
		boolean bGotTremolo_;
		boolean bGotVibrato_;
		boolean bKeyOFF_;
		boolean bNeedToBePlayed_;
		boolean bNoteCutted_;

		int column_Effect_, column_EffectOperand_;
		int column_FineVolumeUp_, column_FineVolumeDown_;

		int column_PanSlidingSpeed_;
		int column_PortamentoSpeed_;
		int column_VolumeSlidingSpeed_;

		int envelopePanning_;
		int envelopeVolume_;
		int fadeOutVolume_;
		int fineSlideSpeed_;

		int fineTune_;
		int fineVolumeUp_, fineVolumeDown_;
		int globalVolSlide_;
		int iVoice_;

		Sample listSamples_;

		int note_, effect_, effectOperand_, effectOperand2_, oldEffect_ = -1;

		int noteCut_;

		int panEnvActuPoint_;
		float panEnvActuPos_;

		int panning_;
		int period_, dstPeriod_, periodBAK_;
		int portamentoSpeed_;
		int portaSpeed_;
		Sample samplePlaying_, sampleToPlay_;
		int samplePosJump_;

		Channel sndchan_;
		int tickBeforeSample_;

		int tickForRetrigNote_ = -1;
		int tremoloCounter_;

		int tremoloForm_;
		int tremoloSpeed_, tremoloProf_;
		int vibratoCounter_, vibratoCounter_s_;
		int vibratoForm_;
		int vibratoSpeed_, vibratoSpeed_s_, vibratoProf_, vibratoProf_s_;

		int volEnvActuPoint_;
		float volEnvActuPos_;
		int volume_, volumeBAK_;

		int volumeSlidingSpeed_;

		Voice() {
			sndchan_ = new Channel();
		}

		void panEnvelope() {
			int x0, y0, x1, y1, actuY;
			int envPan[];

			if (actuInstru_ == null)
				return;

			if ((actuInstru_.panType & 1) == 0)
				return;

			if (tick_ < tickBeforeSample_)
				return;

			envPan = actuInstru_.envPan; // [panEnvActuPoint_];

			boolean bAuthorize = true;

			if (panEnvActuPoint_ == actuInstru_.nbPointsPan - 1) {
				x1 = 1;
				y0 = y1 = envPan[panEnvActuPoint_ * 2 + 1];
			} else {
				x1 = envPan[panEnvActuPoint_ * 2 + 2]
						- envPan[panEnvActuPoint_ * 2];
				y0 = envPan[panEnvActuPoint_ * 2 + 1];
				y1 = envPan[panEnvActuPoint_ * 2 + 3];
			}

			if (x1 == 0) {
				x1 = 1;
				y1 = y0;
			}

			actuY = (int) ((float) ((y1 - y0) * panEnvActuPos_) / x1 + y0);

			if ((actuInstru_.panType & 2) != 0) {
				// Sustain point
				if (panEnvActuPoint_ == actuInstru_.sustainPointPan
						&& bKeyOFF_ == false)
					bAuthorize = false;
			}
			if (bAuthorize == true)
				panEnvActuPos_++;

			if (panEnvActuPos_ >= x1) {
				if ((actuInstru_.panType & 4) != 0) {
					// Envelope Loop
					if (panEnvActuPoint_ + 1 == actuInstru_.endPanLoop) {
						panEnvActuPoint_ = actuInstru_.startPanLoop;
					} else
						panEnvActuPoint_++;
					panEnvActuPos_ = 0;
				} else {
					if (panEnvActuPoint_ == actuInstru_.nbPointsPan - 2)
						panEnvActuPos_ = x1;
					else {
						panEnvActuPoint_++;
						panEnvActuPos_ = 0;
					}
				}
			}
			envelopePanning_ = actuY * 4;
		}

		void panSliding(int pan) {
			panning_ += pan;
		}

		void portamentoTo(int speed) {
			if (period_ < dstPeriod_) {
				period_ += speed;
				if (period_ > dstPeriod_)
					period_ = dstPeriod_;
			} else if (period_ > dstPeriod_) {
				period_ -= speed;
				if (period_ < dstPeriod_)
					period_ = dstPeriod_;
			}

			periodBAK_ = period_;
		}

		void seekEnvelope(int pos) {
			int i;

			if (actuInstru_ == null)
				return;

			// Reseek volume
			if ((actuInstru_.volType & 1) != 0) {
				int nbPoints = actuInstru_.nbPointsVol;
				if (pos >= actuInstru_.envVol[(nbPoints - 1) * 2]) {
					volEnvActuPoint_ = nbPoints - 2;
					volEnvActuPos_ = (float) actuInstru_.envVol[(nbPoints - 1) * 2]
							- actuInstru_.envVol[(nbPoints - 2) * 2];
					return;
				}

				i = 0;
				while (actuInstru_.envVol[i * 2] < pos)
					i++;
				volEnvActuPoint_ = i - 1;
				volEnvActuPos_ = (float) (pos - actuInstru_.envVol[(i - 1) * 2]);
			}

			// then Reseek panning
			if ((actuInstru_.panType & 1) != 0) {
				int nbPoints = actuInstru_.nbPointsPan;
				if (pos >= actuInstru_.envPan[(nbPoints - 1) * 2]) {
					panEnvActuPoint_ = nbPoints - 2;
					panEnvActuPos_ = (float) actuInstru_.envPan[(nbPoints - 1) * 2]
							- actuInstru_.envPan[(nbPoints - 2) * 2];
					return;
				}

				i = 0;
				while (actuInstru_.envPan[(++i) * 2] < pos)
					;
				panEnvActuPoint_ = i - 1;
				panEnvActuPos_ = (float) (pos - actuInstru_.envPan[(i - 1) * 2]);
			}
		}

		void soundUpdate() {
			int i;
			byte data[];
			Sample sample;
			int freq;
			float vol, pan;

			if (samplePlaying_ == null)
				return;

			if (tick_ < tickBeforeSample_)
				return;

			// Clamp the Period voice
			if (period_ < MIN_PERIOD)
				period_ = MIN_PERIOD;
			else if (period_ > MAX_PERIOD)
				period_ = MAX_PERIOD;

			if (freqFlag_ != 0)
				freq = getFreq(period_);
			else
				freq = (int) (((long) (3579546 << 2)) / period_);

			// Sleep(100);

			// Clamp the volume_ voice
			if (volume_ < 0)
				volume_ = 0;
			else if (volume_ > 64)
				volume_ = 64;

			// Clamp the panning_ voice
			if (panning_ < 0)
				panning_ = 0;
			else if (panning_ > 255)
				panning_ = 255;

			if (bNoteCutted_ == false) {
				vol = (fadeOutVolume_ / 65536.f) * (volume_ / 64.f)
						* (envelopeVolume_ / 64.f) * (globalVolume_ / 64.f)
						* mainVolume_;
				if (vol < 0)
					vol = 0;
				else if (vol > 1)
					vol = 1;
			} else
				vol = 0;

			pan = (float) (panning_ + (envelopePanning_ - 127)
					* (128 - Math.abs(panning_ - 128)) / 128);
			pan = (pan - 128) / 128.f;
			if (pan < -1)
				pan = -1;
			else if (pan > 1)
				pan = 1;

			if (bGotRetrigNote_ == true) {
				if (((tick_ - 1) % tickForRetrigNote_) == 0)
					bNeedToBePlayed_ = true;
			}

			if (bNeedToBePlayed_ == false) {
				sndchan_.setPan(pan);

				sndchan_.frequency = freq;
				sndchan_.step = freq / (float) frequency_;
				sndchan_.vol = vol;

				return;
			}

			int startPos;
			if (effect_ == 0x9) {
				effect_ = 0xfff;
				startPos = samplePosJump_;
			} else
				startPos = 0;

			chansList_.removeChannel(sndchan_);

			if (tick_ >= tickBeforeSample_) {

				sndchan_.audio = samplePlaying_.audioData;
				sndchan_.frequency = freq;
				sndchan_.step = freq / (float) frequency_;
				sndchan_.vol = vol;
				sndchan_.setPan(pan);
				sndchan_.pos = startPos;
				chansList_.addChannel(sndchan_);

				bNeedToBePlayed_ = false;
				tickBeforeSample_ = 0;
			}

		}

		void tremolo() {
			int tremoloSeek;
			int volumeOffset;

			tremoloSeek = (tremoloCounter_ >> 2) & 0x3f;

			switch (tremoloForm_) {
			default:
				volumeOffset = TrackerConstant.vibratoTable[tremoloSeek];
				break;
			}

			volumeOffset *= tremoloProf_;
			volumeOffset >>= 6;
			volume_ = volumeBAK_ + volumeOffset;

			tremoloCounter_ += tremoloSpeed_;
		}

		void updateSoundWithEffect() {
			switch (column_Effect_) {

			case COLUMN_EFFECT_VOLUMESLIDE:
				volumeSliding(column_VolumeSlidingSpeed_);
				break;

			case COLUMN_EFFECT_PANSLIDE:
				panSliding(column_PanSlidingSpeed_);
				break;

			case COLUMN_EFFECT_PORTAMENTO_TO:
				portamentoTo(column_PortamentoSpeed_);
				break;
			}

			switch (effect_) {

			case 0x0fff:
				// NOTHING
				break;

			case 0x0:
				// ARPEGGIO
				switch (arpeggioCount_ % 3) {
				case 0:
					period_ = getPeriod(note_ + arp2_, fineTune_);
					break;
				case 1:
					period_ = getPeriod(note_ + arp1_, fineTune_);
					break;
				case 2:
					period_ = getPeriod(note_, fineTune_);
					break;
				}
				arpeggioCount_++;
				break;

			case 0x1:
				// PORTAMENTO UP
				period_ -= portaSpeed_;
				periodBAK_ = period_;
				break;

			case 0x2:
				// PORTAMENTO DOWN
				period_ += portaSpeed_;
				periodBAK_ = period_;
				break;

			case 0x3:
				// PORTAMENTO TO
				portamentoTo(portamentoSpeed_);
				break;

			case 0x4:
				// VIBRATO
				vibrato(false, vibratoForm_);
				break;

			case 0x5:
				// PORTAMENTO TO + VOLUME SLIDING
				portamentoTo(portamentoSpeed_);
				volumeSliding(volumeSlidingSpeed_);
				break;

			case 0x6:
				// VIBRATO + VOLUME SLIDING
				vibrato(false, vibratoForm_);
				volumeSliding(volumeSlidingSpeed_);
				break;

			case 0x7:
				// TREMOLO
				tremolo();
				break;

			case 0xa:
				// VOLUME SLIDING
				volumeSliding(volumeSlidingSpeed_);
				break;

			case 0xec:
				if (tick_ - 1 == noteCut_)
					bNoteCutted_ = true;
				break;

			default:
				// STILL NOTHING ;)
				break;
			}
		}

		void updateSoundWithEnvelope() {
			if (actuInstru_ == null)
				return;

			if (actuInstru_.vibratoProf != 0 && actuInstru_.vibratoSpeed != 0) {
				if (actuInstru_.vibratoSweep != 0)
					vibratoProf_s_ += (64 << 8) / actuInstru_.vibratoSweep;
				else
					vibratoProf_s_ += 64 << 8;
				if (vibratoProf_s_ > 256 * 64)
					vibratoProf_s_ = 256 * 64;
				vibratoSpeed_s_ = actuInstru_.vibratoSpeed;
				vibrato(true, actuInstru_.vibratoType);
			}

			volEnvelope();
			panEnvelope();
		}

		void vibrato(boolean bSample, int form) {
			int vibSeek;
			int periodOffset;

			if (bSample == false)
				vibSeek = (vibratoCounter_ >> 2) & 0x3f;
			else
				vibSeek = (vibratoCounter_s_ >> 2) & 0x3f;

			switch (form) {
			case 0:
			default:
				periodOffset = TrackerConstant.vibratoTable[vibSeek];
				break;
			case 1:
				periodOffset = TrackerConstant.squareTable[vibSeek];
				break;
			case 2:
				periodOffset = (((vibSeek + 32) % 63) - 32) << 3;
				break;
			case 3:
				periodOffset = (-((vibSeek + 32) % 63) + 32) << 3;
				break;
			}

			if (bSample == false) {
				periodOffset *= vibratoProf_;
				periodOffset >>= 7;
				periodOffset <<= 2;
			} else {
				periodOffset *= vibratoProf_s_ * actuInstru_.vibratoProf;
				periodOffset >>= (14 + 8);
			}

			if (bSample == true
					&& (bGotVibrato_ == true || bGotArpeggio_ == true))
				period_ += periodOffset;
			else
				period_ = periodBAK_ + periodOffset;

			if (bSample == false)
				vibratoCounter_ += vibratoSpeed_;
			else
				vibratoCounter_s_ += vibratoSpeed_s_;
		}

		void volEnvelope() {
			int x0, y0, x1, y1, actuY;
			int envVol[];

			if (actuInstru_ == null)
				return;

			if ((actuInstru_.volType & 1) == 0) {
				if (bKeyOFF_ == true)
					fadeOutVolume_ = 0;
				return;
			}

			if (tick_ < tickBeforeSample_)
				return;

			boolean bAuthorize = true;

			envVol = actuInstru_.envVol; // [volEnvActuPoint_];

			if (volEnvActuPoint_ == actuInstru_.nbPointsVol - 1) {
				x1 = 1;
				y0 = y1 = envVol[1];
				bAuthorize = false;
			} else {
				x1 = envVol[volEnvActuPoint_ * 2 + 2]
						- envVol[volEnvActuPoint_ * 2];
				y0 = envVol[volEnvActuPoint_ * 2 + 1];
				y1 = envVol[volEnvActuPoint_ * 2 + 3];
			}

			if (x1 == 0) {
				x1 = 1;
				y1 = y0;
			}

			actuY = (int) ((float) ((y1 - y0) * volEnvActuPos_) / x1 + y0);

			if ((actuInstru_.volType & 2) != 0) {
				// Sustain point
				if (volEnvActuPoint_ == actuInstru_.sustainPointVol
						&& bKeyOFF_ == false)
					bAuthorize = false;
			}
			if (bAuthorize == true)
				volEnvActuPos_++;

			if (volEnvActuPos_ >= x1) {
				if ((actuInstru_.volType & 4) != 0) {
					// Envelope Loop
					if (volEnvActuPoint_ + 1 == actuInstru_.endVolLoop)
						volEnvActuPoint_ = actuInstru_.startVolLoop;
					else
						volEnvActuPoint_++;
					volEnvActuPos_ = 0;
				} else {
					if (volEnvActuPoint_ == actuInstru_.nbPointsVol - 2)
						volEnvActuPos_ = x1;
					else {
						volEnvActuPoint_++;
						volEnvActuPos_ = 0;
					}
				}
			}
			envelopeVolume_ = actuY;

			if (bKeyOFF_ == true) {
				fadeOutVolume_ -= actuInstru_.fadeOut * 2;
				if (fadeOutVolume_ < 0)
					fadeOutVolume_ = 0;
			}
		}

		void volumeSliding(int vol) {
			volume_ += vol;
			volumeBAK_ = volume_;
		}

	}

	protected static final int COLUMN_EFFECT_NONE = 0x0fff;
	protected static final int COLUMN_EFFECT_PANSLIDE = 0x02;
	protected static final int COLUMN_EFFECT_PORTAMENTO_TO = 0x03;
	protected static final int COLUMN_EFFECT_VOLUMESLIDE = 0x01;
	protected static final int MAX_PERIOD = 11520;
	protected static final int MAXNBPATTERNS = 256;
	protected static final int MAXNBSONGPAT = 256;
	protected static final int MAXNBVOICES = 64;
	protected static final int MIN_PERIOD = 40;
	protected boolean bGotPatternLoop_;
	protected int BPM_ = 125;
	protected int freqFlag_;
	protected int globalVolume_ = 64;
	protected Instru instrus_[];
	protected int listLen_;
	protected int listPatterns_[] = new int[256];
	protected float mainVolume_ = 1.0f;
	protected int nbInstrus_;
	protected int nbPatterns_;
	protected int nbVoices_;
	protected int patternDelay_ = -1;
	protected int patternLoopLeft_;
	protected int patternPosLoop_;
	protected Pattern patterns_[];
	protected int posChanson_ = 0;
	protected int posInPattern_;
	protected int posRestart_;
	protected int sizHeaderInfo_;
	protected byte songName_[] = new byte[20];
	protected byte trackerName_[] = new byte[20];
	protected int version_;
	protected Voice voices_[];
	@Override
	public String getSongName() {
		return new String(songName_, Charset.forName("UTF-8"));
	}
	public XMPlayer(IAudioDevice audioDevice) {
		super(audioDevice);
		speed_ = 6;
	}
	@Override
	protected void dispatchNotes() {
		int iInstru, iSample;
		int frequency;
		int note, effect, effectOperand_, colum, effectOperand2_;
		int posJump;
		int iVoice;

		int actuPattern = listPatterns_[posChanson_];
		if (actuPattern >= nbPatterns_)
			actuPattern = 0;
		int actuPos = posInPattern_;

		int newSpeed = 0, newBPM = 0;
		int nbLines = patterns_[actuPattern].nbLines;

		boolean bGotPatternJump = false;
		boolean bGotPatternBreak = false;
		int whereToJump = 0;
		int whereToBreak = 0;

		for (iVoice = 0; iVoice < nbVoices_; iVoice++) {

			// if (iVoice != 4)
			// continue;

			Voice voice = voices_[iVoice];
			Column actuColumn = patterns_[actuPattern].columns[iVoice];

			note = actuColumn.notesInfo[actuPos].note;
			effect = actuColumn.notesInfo[actuPos].effect;
			effectOperand_ = actuColumn.notesInfo[actuPos].effectOperand;
			colum = actuColumn.notesInfo[actuPos].colum;
			iInstru = actuColumn.notesInfo[actuPos].iInstru;

			// Reset GotVibrato if Vibrato no more used
			if (((effect != 0x4) && (effect != 0x6))
					&& (voice.bGotVibrato_ == true)) {
				voice.period_ = voice.periodBAK_;
				voice.bGotVibrato_ = false;
			}
			// Reset GotTremolo if Tremolo no more used
			if ((effect != 0x7) && (voice.bGotTremolo_ == true)) {
				voice.volume_ = voice.volumeBAK_;
				voice.bGotTremolo_ = false;
			}
			// For safety, restore Period after Arpeggio
			if (voice.bGotArpeggio_ == true)
				voice.period_ = voice.periodBAK_;

			boolean bAllowToUpdateNote = true;

			if (voice.samplePlaying_ != null) {
				if ((effect == 0x03) || (effect == 0x05)
						|| ((colum >= 0xf0) && (colum <= 0xff)))
					bAllowToUpdateNote = false;
			}

			/*
			 * Added iInstru<instrus_.length to prevent array bounds error
			 */
			if (iInstru >= 0 && iInstru<instrus_.length) {
				// if (iInstru > nbInstrus_)
				// voice.actuInstru_ = NULLInstru_;
				// else
				voice.actuInstru_ = instrus_[iInstru];

				if (voice.samplePlaying_ != null) {
					voice.volume_ = voice.samplePlaying_.volume;
					voice.volumeBAK_ = voice.volume_;
					voice.panning_ = voice.samplePlaying_.panning;
					voice.fineTune_ = voice.samplePlaying_.fineTune;
					voice.vibratoProf_s_ = 0;
				}

				voice.envelopeVolume_ = 64;
				voice.fadeOutVolume_ = 65536;
				voice.envelopePanning_ = 127;
				voice.volEnvActuPos_ = 0;
				voice.volEnvActuPoint_ = 0;
				voice.panEnvActuPos_ = 0;
				voice.panEnvActuPoint_ = 0;
				voice.bKeyOFF_ = false;
			}

			if (note > 0 && note < 97 && voice.actuInstru_ != null) {
				Sample sample;
				int sampleToPick;
				sampleToPick = voice.actuInstru_.sampleTable[note - 1];

				// if (sampleToPick >= voice.actuInstru_.nbSamples)
				// sample = NULLSample_;
				// else
				sample = voice.actuInstru_.samples[sampleToPick];

				voice.note_ = note + sample.relativeNote;
				voice.tremoloCounter_ = 0;
				voice.vibratoCounter_ = 0;
				voice.vibratoCounter_s_ = 0;

				if (iInstru >= 0) {
					voice.volume_ = sample.volume;
					voice.volumeBAK_ = voice.volume_;
					voice.panning_ = sample.panning;
					voice.fineTune_ = sample.fineTune;
					voice.vibratoProf_s_ = 0;
				}

				if (bAllowToUpdateNote == true) {
					int period;
					int rel = note + sample.relativeNote;
					if (rel < 0)
						rel = 0;
					period = getPeriod(rel, voice.fineTune_);

					voice.samplePlaying_ = sample;
					voice.period_ = period;
					voice.periodBAK_ = voice.period_;
					voice.bNoteCutted_ = false;

					voice.dstPeriod_ = period;
					voice.bNeedToBePlayed_ = true;
				}
			} else if (note == 97) {
				voice.bKeyOFF_ = true;
			}

			voice.bGotArpeggio_ = false;
			voice.bGotRetrigNote_ = false;
			voice.effect_ = 0x0fff;
			voice.column_Effect_ = COLUMN_EFFECT_NONE;

			// volume_ Column
			if (colum >= 0x10 && colum < 0x60) {
				voice.volume_ = colum - 0x10;
				voice.volumeBAK_ = voice.volume_;
			} else if (colum >= 0x60 && colum < 0x70) {
				// volume_ Sliding down
				colum -= 0x60;
				voice.column_Effect_ = COLUMN_EFFECT_VOLUMESLIDE;
				if (colum != 0)
					voice.column_VolumeSlidingSpeed_ = -colum;
			} else if (colum >= 0x70 && colum < 0x80) {
				// volume_ Sliding up
				colum -= 0x70;
				voice.column_Effect_ = COLUMN_EFFECT_VOLUMESLIDE;
				voice.effect_ = 0xa;
				if (colum != 0)
					voice.column_VolumeSlidingSpeed_ = colum;
			} else if (colum >= 0x80 && colum < 0x90) {
				// Fine volume_ down
				colum -= 0x80;
				if (colum != 0)
					voice.column_FineVolumeDown_ = colum;
				voice.volume_ -= voice.column_FineVolumeDown_;
				voice.volumeBAK_ = voice.volume_;
			} else if (colum >= 0x90 && colum < 0xa0) {
				// Fine volume_ up
				colum -= 0x90;
				if (colum != 0)
					voice.column_FineVolumeUp_ = colum;
				voice.volume_ += voice.column_FineVolumeUp_;
				voice.volumeBAK_ = voice.volume_;
			} else if (colum >= 0xa0 && colum < 0xb0) {
				voice.vibratoSpeed_ = colum - 0xa0;
			} else if (colum >= 0xb0 && colum < 0xc0) {
				voice.vibratoProf_ = colum - 0xb0;
			} else if (colum >= 0xc0 && colum < 0xd0) {
				voice.panning_ = (((colum - 0xc0) + 1) * 16) - 1;
			} else if (colum >= 0xd0 && colum < 0xe0) {
				colum -= 0xd0;
				voice.column_Effect_ = COLUMN_EFFECT_PANSLIDE;
				if (colum != 0)
					voice.column_PanSlidingSpeed_ = -colum;
			} else if (colum >= 0xe0 && colum < 0xf0) {
				colum -= 0xd0;
				voice.column_Effect_ = COLUMN_EFFECT_PANSLIDE;
				if (colum != 0)
					voice.column_PanSlidingSpeed_ = colum;
			} else if (colum >= 0xf0 && colum <= 0xff) {
				// PORTAMENTO TO
				colum -= 0xf0;
				if (note != 0 && note != 97 && voice.samplePlaying_ != null)
					voice.dstPeriod_ = getPeriod(note
							+ voice.samplePlaying_.relativeNote,
							voice.fineTune_);
				voice.column_Effect_ = COLUMN_EFFECT_PORTAMENTO_TO;
				if (colum != 0)
					voice.column_PortamentoSpeed_ = colum << 6;
			}

			// Standart effect
			switch (effect) {
			case 0x0:
				// ARPEGGIO
				if (effectOperand_ != 0) {
					voice.effect_ = effect;
					voice.effectOperand_ = effectOperand_;
					voice.arp1_ = effectOperand_ & 0xf;
					voice.arp2_ = effectOperand_ >> 4;
					voice.bGotArpeggio_ = true;
					voice.arpeggioCount_ = 0;
				}
				;
				break;

			case 0x1:
				// PORTAMENTO UP
				voice.effect_ = effect;
				if (effectOperand_ != 0)
					voice.portaSpeed_ = effectOperand_ * 4;
				break;

			case 0x2:
				// PORTAMENTO DOWN
				voice.effect_ = effect;
				if (effectOperand_ != 0)
					voice.portaSpeed_ = effectOperand_ * 4;
				break;
			case 0x3:
				// PORTAMENTO TO
				if (note > 0 && note < 97 && voice.samplePlaying_ != null)
					voice.dstPeriod_ = getPeriod(note
							+ voice.samplePlaying_.relativeNote,
							voice.fineTune_);
				voice.effect_ = effect;
				if (effectOperand_ != 0)
					voice.portamentoSpeed_ = effectOperand_ * 4;
				break;

			case 0x4:
				// VIBRATO
				voice.effect_ = effect;
				if ((effectOperand_ & 0xf0) != 0)
					voice.vibratoSpeed_ = (effectOperand_ >> 4) * 4;
				if ((effectOperand_ & 0x0f) != 0)
					voice.vibratoProf_ = effectOperand_ & 0x0f;
				break;

			case 0x5:
				// PORTAMENTO TO + VOLUME SLIDING
				if (note != 0 && voice.samplePlaying_ != null)
					voice.dstPeriod_ = getPeriod(note
							+ voice.samplePlaying_.relativeNote,
							voice.fineTune_);
				voice.effect_ = effect;
				if ((effectOperand_ & 0xf0) != 0)
					voice.volumeSlidingSpeed_ = effectOperand_ >> 4;
				else if ((effectOperand_ & 0x0f) != 0)
					voice.volumeSlidingSpeed_ = -(effectOperand_ & 0x0f);
				break;

			case 0x6:
				// VIBRATO + VOLUME SLIDING
				voice.effect_ = effect;
				if ((effectOperand_ & 0xf0) != 0)
					voice.volumeSlidingSpeed_ = effectOperand_ >> 4;
				else if ((effectOperand_ & 0x0f) != 0)
					voice.volumeSlidingSpeed_ = -(effectOperand_ & 0x0f);
				break;

			case 0x7:
				// TREMOLO
				voice.effect_ = effect;
				if ((effectOperand_ & 0xf0) != 0)
					voice.tremoloSpeed_ = (effectOperand_ >> 4) * 4;
				if ((effectOperand_ & 0x0f) != 0)
					voice.tremoloProf_ = effectOperand_ & 0x0f;
				break;

			case 0x8:
				// PANNING
				voice.panning_ = effectOperand_;
				break;

			case 0x9:
				// SAMPLE JUMP
				voice.effect_ = effect;
				if (effectOperand_ != 0)
					voice.samplePosJump_ = effectOperand_ << 8;
				break;

			case 0xa:
				// VOLUME SLIDING
				voice.effect_ = effect;
				if ((effectOperand_ & 0xf0) != 0)
					voice.volumeSlidingSpeed_ = effectOperand_ >> 4;
				else if ((effectOperand_ & 0x0f) != 0)
					voice.volumeSlidingSpeed_ = -(effectOperand_ & 0xf);
				break;

			case 0xb:
				// POSITION JUMP
				bGotPatternJump = true;
				whereToJump = effectOperand_;
				break;

			case 0xc:
				// SET VOLUME
				voice.effect_ = effect;
				voice.volume_ = effectOperand_;
				voice.volumeBAK_ = voice.volume_;
				break;

			case 0xd:
				// PATTERN BREAK
				bGotPatternBreak = true;
				whereToBreak = ((effectOperand_ & 0xf0) >> 4) * 10
						+ (effectOperand_ & 0x0f);
				// Yes, posJump is given in BCD format. What is the interest ?
				// Absolutely none, thanks XM..
				break;

			case 0xe:
				// MISCELLANEOUS
				effectOperand2_ = effectOperand_ & 0xf;
				effectOperand_ >>= 4;

				switch (effectOperand_) {
				case 0x1:
					// FineSlideUp
					if (effectOperand2_ != 0)
						voice.fineSlideSpeed_ = effectOperand2_ * 4;
					voice.period_ -= voice.fineSlideSpeed_;
					voice.periodBAK_ = voice.period_;
					break;
				case 0x2:
					// FineSlideDown
					if (effectOperand2_ != 0)
						voice.fineSlideSpeed_ = effectOperand2_ * 4;
					voice.period_ += voice.fineSlideSpeed_;
					voice.periodBAK_ = voice.period_;
					break;
				case 0x4:
					// Set Vibrato Form
					voice.vibratoForm_ = effectOperand2_;
					break;
				case 0x5:
					// Set fineTune
					if (note == 0)
						break;
					voice.fineTune_ = effectOperand2_ << 4;
					break;
				case 0x6:
					// Pattern Loop
					if (effectOperand2_ != 0) {
						if (bGotPatternLoop_ == false) {
							bGotPatternLoop_ = true;
							patternLoopLeft_ = effectOperand2_;
						}
						patternLoopLeft_--;
						if (patternLoopLeft_ >= 0)
							posInPattern_ = patternPosLoop_ - 1;
						else
							bGotPatternLoop_ = false;
					} else
						patternPosLoop_ = posInPattern_;
					break;
				case 0x7:
					// Set Tremolo Form
					voice.tremoloForm_ = effectOperand2_;
					break;
				case 0x9:
					// Retrigger note
					if (effectOperand2_ == 0)
						break;
					voice.bGotRetrigNote_ = true;
					voice.tickForRetrigNote_ = effectOperand2_;
					break;
				case 0xa:
					// Fine Volumesliding Up
					if (effectOperand2_ != 0)
						voice.fineVolumeUp_ = effectOperand2_;
					voice.volume_ += voice.fineVolumeUp_;
					voice.volumeBAK_ = voice.volume_;
					break;
				case 0xb:
					// Fine Volumesliding Down
					if (effectOperand2_ != 0)
						voice.fineVolumeDown_ = effectOperand2_;
					voice.volume_ -= voice.fineVolumeDown_;
					voice.volumeBAK_ = voice.volume_;
					break;
				case 0xc:
					// note Cut
					voice.effect_ = 0xec;
					if (effectOperand2_ != 0)
						voice.noteCut_ = effectOperand2_;
					else
						voice.bNoteCutted_ = true;
					break;
				case 0xd:
					// note Delay
					voice.tickBeforeSample_ = effectOperand2_ + 1;
					break;
				case 0xe:
					// Pattern Delay
					if (patternDelay_ < 0)
						patternDelay_ = effectOperand2_;
					break;
				}
				break;

			case 0xf:
				// SET SPEED or BPM_
				if (effectOperand_ < 32)
					newSpeed = effectOperand_;
				else
					newBPM = effectOperand_;
				break;

			case 0xf + 'g' - 'f':
				// SET GLOBAL VOLUME
				globalVolume_ = effectOperand_;
				if (globalVolume_ > 64)
					globalVolume_ = 64;
				break;

			case 0xf + 'h' - 'f':
				// GLOBAL VOLUME SLIDING
				if (effectOperand_ != 0)
					voice.globalVolSlide_ = effectOperand_;
				if ((voice.globalVolSlide_ & 0xf0) != 0)
					globalVolume_ += (voice.globalVolSlide_ >> 4) * 4;
				else if ((voice.globalVolSlide_ & 0x0f) != 0)
					globalVolume_ -= (voice.globalVolSlide_ & 0xf) * 4;
				if (globalVolume_ > 64)
					globalVolume_ = 64;
				else if (globalVolume_ < 0)
					globalVolume_ = 0;
				break;

			case 0xf + 'r' - 'f':
				// MULTI RETRIG NOTE
				voice.bGotRetrigNote_ = true;
				if ((effectOperand_ & 0x0f) != 0)
					voice.tickForRetrigNote_ = effectOperand_ & 0x0f;
				break;

			case 0xf + 'l' - 'f':
				// SET ENVELOPE POSITION
				voice.seekEnvelope(effectOperand_);
				break;

			default:
				// UNKNOWN EFFECT
				break;
			}

			if ((voice.column_Effect_ == COLUMN_EFFECT_VOLUMESLIDE)
					&& (voice.effect_ == 0xa))
				voice.column_Effect_ = COLUMN_EFFECT_NONE;

			if (((voice.effect_ == 0x4) || (voice.effect_ == 0x6))
					&& (voice.bGotVibrato_ == false))
				voice.bGotVibrato_ = true;

			if ((voice.effect_ == 0x7) && (voice.bGotTremolo_ == false))
				voice.bGotTremolo_ = true;
		}

		if (newSpeed != 0)
			speed_ = newSpeed;
		if (newBPM != 0)
			BPM_ = newBPM;

		posInPattern_++;

		if ((posInPattern_ == patterns_[actuPattern].nbLines)
				|| (bGotPatternJump == true) || (bGotPatternBreak == true)) {
			posInPattern_ = whereToBreak;
			if (bGotPatternJump == true)
				posChanson_ = whereToJump;
			else
				posChanson_++;

			if (posChanson_ >= listLen_) {
				posChanson_ = 0;
				posInPattern_ = 0;

				if (loopable_ == true) {
					posChanson_ = posRestart_;
					if (posRestart_ == 0) {
						globalVolume_ = 64;
						for (int i = 0; i < nbVoices_; i++) {
							chansList_.removeChannel(voices_[i].sndchan_);
							voices_[i].actuInstru_ = null;
							voices_[i].samplePlaying_ = null;
						}
					}
				} else {
					running_ = false;
				}

			}
		}

	}

	/**
	 * Gets the current reading position of the song.
	 * 
	 * @return The current position.
	 */
	public int getCurrentPos() {
		return posInPattern_;
	}
	/**
	 * Gets the current reading row of the song.
	 * 
	 * @return The current row.
	 */
	public int getCurrentRow() {
		return posChanson_;
	}
	
	protected int getFreq(int period) {
		int okt;
		int frequency;

		period = 11520 - period;
		okt = period / 768 - 3;
		frequency = TrackerConstant.lintab[period % 768];

		if (okt < 8)
			frequency = frequency >> (7 - okt);
		else {
			frequency = frequency << (okt - 7);
		}

		return frequency;
	}
	protected int getLinearPeriod(int note, int fine) {
		return ((10 * 12 * 16 * 4) - ((int) note * 16 * 4) - (fine / 2) + 64);
	}
	protected int getLogPeriod(int note, int fine) {
		int n, o;
		int p1, p2, i;

		// fine+=128;
		n = note % 12;
		o = note / 12;
		i = (n << 3) + (fine >> 4); // n*8 + fine/16
		if (i < 0)
			i = 0;

		p1 = TrackerConstant.logtab[i];
		p2 = TrackerConstant.logtab[i + 1];

		return (interpolate(fine / 16, 0, 15, p1, p2) >> o);
	}
	/**
	 * Gets the internal buffer used to mix the samples together. It can be used
	 * for instance to analyze the wave, apply effect or whatever.
	 * 
	 * @return The internal mix buffer.
	 */
	public byte[] getMixBuffer() {
		return pcm_;
	}

	protected int getPeriod(int note, int fine) {
		int period;
		if (freqFlag_ != 0)
			period = getLinearPeriod(note, fine);
		else
			period = getLogPeriod(note, fine);
		return period;
	}
	protected int interpolate(int p, int p1, int p2, int v1, int v2) {
		int dp, dv, di;

		if (p1 == p2)
			return v1;

		dv = v2 - v1;
		dp = p2 - p1;
		di = p - p1;

		return v1 + ((int) (di * dv) / dp);
	}
	

	/**
	 * Loads the XM.
	 * 
	 * @param _input
	 *            An instance to a PipeIn Class to read data from disk or URL.
	 * @return NOERR if no error occured.
	 */
	@Override
	public void load(byte[] bytes) {
		SeekableBytes _input=new SeekableBytes(bytes, Endian.LITTLEENDIAN);
		byte tmp[] = new byte[20];
		int lseek;

		_input.read(tmp, 0, 17);
		String ID = new String(tmp).substring(0, 17);
		if (ID.compareTo("Extended Module: ") != 0)
			throw new OZModRuntimeError(OZMod.ERR.BADFORMAT);

		_input.readFully(songName_);
		_input.seek(38);
		_input.read(trackerName_, 0, 20);
		version_ = _input.readUShort();
		lseek = _input.tell();

		sizHeaderInfo_ = _input.readInt();
		listLen_ = _input.readUShort();
		posRestart_ = _input.readUShort();
		nbVoices_ = _input.readUShort();
		if (nbVoices_ > 64)
			throw new OZModRuntimeError(OZMod.ERR.BADFORMAT);

		nbPatterns_ = _input.readUShort();
		nbInstrus_ = _input.readUShort();
		freqFlag_ = _input.readUShort();
		speed_ = _input.readUShort();
		BPM_ = _input.readUShort();

		// don't trust on nbPatterns_, always read 256 bytes!
		for (int i = 0; i < 256; i++)
			// for (int i = 0; i < nbPatterns_; i++)
			listPatterns_[i] = _input.readUByte();

		voices_ = new Voice[nbVoices_];
		for (int i = 0; i < nbVoices_; i++) {
			Voice voice = new Voice();
			voices_[i] = voice;
			voice.iVoice_ = i;
		}

		_input.seek(lseek + sizHeaderInfo_);

		// Patterns
		patterns_ = new Pattern[nbPatterns_];
		for (int i = 0; i < nbPatterns_; i++) {
			Pattern pat = new Pattern();
			patterns_[i] = pat;

			int headerSize;
			int patternCompression;
			int comp;

			headerSize = _input.readInt();
			patternCompression = _input.readUByte();
			pat.nbLines = _input.readUShort();
			comp = _input.readUShort();

			pat.columns = new Column[nbVoices_];
			for (int j = 0; j < nbVoices_; j++) {
				Column column = new Column();
				pat.columns[j] = column;
				column.notesInfo = new NoteInfo[pat.nbLines];
				for (int k = 0; k < pat.nbLines; k++)
					column.notesInfo[k] = new NoteInfo();
			}

			if (comp == 0)
				continue;

			for (int j = 0; j < pat.nbLines; j++) {
				for (int k = 0; k < nbVoices_; k++) {
					int dat;
					dat = _input.readUByte();

					NoteInfo noteInfo = pat.columns[k].notesInfo[j];

					if ((dat & 0x80) != 0) {
						// packed info
						if ((dat & 1) != 0)
							noteInfo.note = _input.readUByte();

						if ((dat & 2) != 0) {
							noteInfo.iInstru = _input.readUByte();
							noteInfo.iInstru--;
						}

						if ((dat & 4) != 0)
							noteInfo.colum = _input.readUByte();

						if ((dat & 8) != 0)
							noteInfo.effect = _input.readUByte();

						if ((dat & 16) != 0)
							noteInfo.effectOperand = _input.readUByte();
					} else {
						noteInfo.note = dat;
						noteInfo.iInstru = _input.readUByte();
						noteInfo.iInstru--;
						noteInfo.colum = _input.readUByte();
						noteInfo.effect = _input.readUByte();
						noteInfo.effectOperand = _input.readUByte();
					}
				}
			}
		}

		// Check for unexisting pattern
		for (int i = 0; i < listLen_; i++) {
			int iPat = listPatterns_[i];
			if (patterns_[iPat] == null) {
				Pattern pat = new Pattern();
				patterns_[i] = pat;
				pat.nbLines = 64;
				pat.columns = new Column[nbVoices_];
				for (int j = 0; j < nbVoices_; j++) {
					Column column = new Column();
					pat.columns[j] = column;
					column.notesInfo = new NoteInfo[pat.nbLines];
					for (int k = 0; k < pat.nbLines; k++)
						column.notesInfo[k] = new NoteInfo();
				}
			}
		}

		// Instruments
		instrus_ = new Instru[nbInstrus_];
		for (int i = 0; i < nbInstrus_; i++) {
			Instru instru = new Instru();
			instrus_[i] = instru;

			lseek = _input.tell();
			
			int headerSize;
			int extra_size;
			headerSize = _input.readInt();
			_input.read(instru.name, 0, 22);
			instru.type = _input.readUByte();
			instru.nbSamples = _input.readUShort();

			/*
			 * What am I for?
			 */
			if ((instru.nbSamples == 0) || (instru.nbSamples > 255)) {
				_input.forward(headerSize - 29);
				continue;
			}

			extra_size = _input.readInt();
			for (int j = 0; j < 96; j++)
				instru.sampleTable[j] = _input.readUByte();

			for (int j = 0; j < 24; j++)
				instru.envVol[j] = _input.readUShort();

			for (int j = 0; j < 24; j++)
				instru.envPan[j] = _input.readUShort();

			instru.nbPointsVol = _input.readUByte();
			instru.nbPointsPan = _input.readUByte();
			instru.sustainPointVol = _input.readUByte();
			instru.startVolLoop = _input.readUByte();
			instru.endVolLoop = _input.readUByte();
			instru.sustainPointPan = _input.readUByte();
			instru.startPanLoop = _input.readUByte();
			instru.endPanLoop = _input.readUByte();
			instru.volType = _input.readUByte();
			instru.panType = _input.readUByte();
			instru.vibratoType = _input.readUByte();
			instru.vibratoSweep = _input.readUByte();
			instru.vibratoProf = _input.readUByte();
			instru.vibratoSpeed = _input.readUByte();
			instru.fadeOut = _input.readUShort();
			instru.reserved = _input.readUShort();

			// Inside the instruments, dispatch samples info
			instru.samples = new Sample[instru.nbSamples];
			for (int j = 0; j < instru.nbSamples; j++) {
				Sample sample = new Sample();
				instru.samples[j] = sample;

				sample.len = _input.readInt();
				sample.startLoop = _input.readInt();
				sample.lengthLoop = _input.readInt();
				sample.volume = _input.readUByte();
				sample.fineTune = _input.readByte();
				sample.type = _input.readUByte();
				sample.panning = _input.readUByte();
				sample.relativeNote = _input.readByte();
				_input.forward(1);
				_input.read(sample.name, 0, 22);
				_input.forward(extra_size - 40);
			}

			// and in a second pass _input.read the audio data (XM format really
			// sucks!!)
			for (int j = 0; j < instru.nbSamples; j++) {
				Sample sample = instru.samples[j];
				if (sample.len == 0)
					continue;

				byte dat[] = new byte[sample.len];
				_input.read(dat, 0, sample.len);

				// samples are stored as delta value, recompose them as absolute
				// integer
				int nbBits = 0;
				if ((sample.type & 16) != 0) {
					// 16 bits
					nbBits = 16;
					int old = 0;
					for (int ii = 2; ii < dat.length; ii += 2) {
						int b1 = (int) (dat[ii] & 0xff);
						int b2 = (int) (dat[ii + 1]);
						int n = b1 | (b2 << 8);

						n += old;
						dat[ii] = (byte) (n & 0xff);
						dat[ii + 1] = (byte) ((n >> 8) & 0xff);

						old = n;
					}
				} else {
					// 8 bits
					nbBits = 8;
					int old = 0;
					for (int ii = 0; ii < dat.length; ii++) {
						int n = dat[ii] + old;
						dat[ii] = (byte) n;
						old = n;
					}
				}

				if ((sample.type & 3) == 1) {
					// forward loop
					sample.audioData
							.make(dat,
									nbBits,
									1,
									(sample.startLoop) >> (nbBits / 16),
									(sample.startLoop + sample.lengthLoop) >> (nbBits / 16),
									AudioData.LOOP_FORWARD);
				} else if (((sample.type & 3) == 2) || ((sample.type & 3) == 3)) {
					// pingpong loop
					sample.audioData
							.make(dat,
									nbBits,
									1,
									(sample.startLoop) >> (nbBits / 16),
									(sample.startLoop + sample.lengthLoop) >> (nbBits / 16),
									AudioData.LOOP_PINGPONG);
				} else
					sample.audioData.make(dat, nbBits, 1);
			}
		} // Next instrument
	}
	protected void oneShot(int _timer) {
		if (tick_ == speed_)
			tick_ = 0;
		tick_++;

		if (tick_ == 1) {
			patternDelay_--;
			if (patternDelay_ < 0)
				dispatchNotes();
		} else {
			for (int i = 0; i < nbVoices_; i++)
				voices_[i].updateSoundWithEffect();
		}

		for (int i = 0; i < nbVoices_; i++)
			voices_[i].updateSoundWithEnvelope();

		for (int i = 0; i < nbVoices_; i++)
			voices_[i].soundUpdate();

		mixSample(_timer);
	}

	/**
	 * Starts to play the XM. The time latency between a note is read and then
	 * heard is approximatively of 100ms. If the XM is not loopable and finish,
	 * you cannot restart it by invoking again this method.
	 */
	@Override
	public void play() {
		if (isAlive() == true || done_ == true)
			return;

		tick_ = 0;
		patternDelay_ = -1;

		running_ = true;

		start();
	}
	/**
	 * Never call this method directly. Use play() instead.
	 */
	@Override
	public void run() {
		frequency_ = 44100;

		int soundBufferLen = frequency_ * 4;
		pcm_ = new byte[soundBufferLen];
		pcms_ = new short[pcm_.length / 2];

		long cumulTime = 0;

		while (running_) {
			
			float timerRate = 1000.0f / (BPM_ * 0.4f);
			int intTimerRate = (int) Math.floor(timerRate);
			long since = timer_.getDelta();
			if (paused) {
				doSleep(100);
				continue;
			}			
			cumulTime += since;

			if (cumulTime >= intTimerRate) {
				cumulTime -= intTimerRate;
				oneShot(intTimerRate);
			}
			doSleep((intTimerRate - cumulTime)/2);
			totalTime += since;
			if (maxPlayTime>0 && totalTime>maxPlayTime) {
				done();
			}
		}
		done();
	}
	
	@Override
	public void setVolume(float _vol) {
		this.mainVolume_=_vol;
	}
	@Override
	public float getVolume() {
		return mainVolume_;
	}
}
