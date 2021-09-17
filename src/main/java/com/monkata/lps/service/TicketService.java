/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.monkata.lps.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.monkata.lps.Game.BouleClient;
import com.monkata.lps.Game.Game;
import com.monkata.lps.Game.GameMaster;
import com.monkata.lps.Game.ModeGame;
import com.monkata.lps.Game.ParamsGame;
import com.monkata.lps.Game.Ticket;
import com.monkata.lps.Game.TicketClient;
import com.monkata.lps.Game.UtilGame;
import com.monkata.lps.Game.WinName;
import com.monkata.lps.Helper.Log;
import com.monkata.lps.Request.RBoule;
import com.monkata.lps.Tiraj.NumberFormater;
import com.monkata.lps.Tiraj.NumberFormater.FreeWinLots;
import com.monkata.lps.Tiraj.NumberFormater.WinLots;
import com.monkata.lps.Tiraj.Tiraj;
import com.monkata.lps.dao.BouleClientRepository;
import com.monkata.lps.dao.GameMasterRepository;
import com.monkata.lps.dao.TicketClientRepository;
import com.monkata.lps.dao.TicketRepository;
import com.monkata.lps.dao.TirajRepository;
import com.monkata.lps.dao.WinNameRepository;
import com.monkata.lps.entity.UserEntity;
import com.monkata.lps.response.JwtResponse;

import dto.Sold;
import lombok.Data;

@Component
public class TicketService {

    @Autowired
    private TicketRepository ticket;
    
    @Autowired
    private TicketClientRepository ticketc;
    
    @Autowired
    JwtUserDetailsService user;
    
    @Autowired
    BouleClientRepository bclient;
    
    @Autowired
    TirajRepository tiraj;
   
	@Autowired
	WinNameRepository wnRep;
	
	@Autowired
	private GameMasterRepository mstgame;
    
	
	public Page<Ticket> getTicketByPage(int size, int page, Long id, String d) {
		Pageable paging = PageRequest.of(page, size, Sort.by("id"));
        Page<Ticket> pagedResult = ticket.getTicketByPage(paging, id, d);
        return  pagedResult;
	}
	
	
	public boolean   checkTotalSoldTicktets(List<RBoule> nbs, String un, int pos){
      UserEntity ue = user.getUserInfo(un); 
        float t = 0;
		for(RBoule r : nbs) {
			t += r.getMontant();
		}
		if(pos==1) {
			return ue.getCompte()>= t;
		} else {
			return ue.getBonus()>= t;
		}
		
	}
	
	
	public double   getTotalSoldTicktets(List<RBoule> nbs){
	        double t = 0;
			for(RBoule r : nbs) {
				t += r.getMontant();
			}
			return t;
	}
	
	
	public double   getMaxwin(List<BouleClient> nbs){
        double t = 0;
		for(BouleClient  r : nbs) {
			t += r.getPwin();
		}
		return t;
}
	
	
	public MaxSellError checkLotsOfTickets(List<RBoule> nbs, Game CGAME) {
		MaxSellError mse = new MaxSellError();
		 for(RBoule nb : nbs) {
			ModeGame mg = UtilGame.getModeGame(CGAME, nb.getId_mg());
			Optional<Double> m = bclient.getTotalLotsSell(this.getDate(),nb.getId_mg(),CGAME.getId());
			if(!m.isPresent()) {
				continue;
			}
			double nm = m.get();
			double mt = nm + nb.getMontant();
            if(mt> mg.getMax_sell()) {
            	mse.setError(true);
            	mse.setRb(nb);
            	mse.setA_sell(nm);
            	mse.setMax_sell(mg.getMax_sell());
            	break;
            }
		 }
		return mse;
	}
	
	
	
	
	  public String   getDate(){
		  LocalDateTime  localDateTime = LocalDateTime.now();
		  DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
		  String   sd = localDateTime.format(formatter);
		  return  sd.split(" ")[0];
	  }
	 
	@Data
    public class MaxSellError{
    	RBoule rb;
    	boolean error;
    	
    	double max_sell;
    	
    	double a_sell;
    	
    	public MaxSellError() {
    		this.error = false;
    	}
    }
	
	
	public TicketClient getTicketClientById(Long idu ,Long  id) {
		return ticketc.getTicketById(idu, id);
	}

    @Transactional
	public VResp verify(Long idu, Long id, ParamsGame pg ) {
		TicketClient tk = this.getTicketClientById(idu, id);
		if(!tk.isOver()) {
	    LocalDateTime ld = tk.getDate_exp();
	    LocalDate now = LocalDate.now();
	    
	    // check if game is draw
	    Tiraj tj = tiraj.isGameDrawToday(tk.getSdatet(), tk.getId_game());
		if(tj==null) {
		   VResp vr = new VResp(tk,"Tiraj la poko fèt",203);
		   return vr;	
		}
	    // CHeck if date 
	    if(ld.toLocalDate().isAfter(now)) {
		    VResp vr = new VResp(tk,"Fich sa expire "+ld.toString(),202);
			return vr;
	    }
	    
		List<WinName> w  = this.wnRep.findAll(); 


		NumberFormater nf = new NumberFormater(tj, w);
		Game game  = getGame(tk.getId_game(), pg.getGames());
		List<WinLots> lwl = new ArrayList<>();
		for(BouleClient bc : tk.getLots()) {
		 WinLots wl =	nf.checkNumberIsWin(bc, getMG(bc.getCode_mg(),game));
		 if(wl!=null) {
		   lwl.add(wl);
		 }
		}
		
		double sold = 0;
		for(WinLots wl : lwl) {
			double win = getSoldByLot(wl);
			sold += win;
			for(int i =0; i<tk.getLots().size(); i++) {
				  if(wl.getBc().getId() == tk.getLots().get(i).getId()) {
					  tk.getLots().get(i).setWin(1);
					  tk.getLots().get(i).setWin_price(win);
				      bclient.save(tk.getLots().get(i));
				      break;
				  }
			}
		}
		tk.setWin_pay(sold);
		tk.setOver(true);
		tk.setPay(true);
		tk.setDate_pay(LocalDate.now());
		tk = ticketc.save(tk);
	    user.addAmount(sold, idu);
		VResp vr = new VResp(lwl, tk, nf.getLots(), sold);
		vr.setCode(200);
		if(sold>0) {
		  vr.setMsg("Bravo !!! ou fè HTG "+sold);
		  nots.add(idu,"Ou fèk sot genyen "+sold+"G nan yon fich.",1L);
		} else {
			vr.setMsg("Pa gen youn nan boul ki nan fich ou a ki soti, Ou fè HTG "+sold);
		}
		return vr ;
		} else {
			VResp vr = new VResp(tk,"Fich sa verifye deja",201);
			return vr;
		}
	}
    
    @Autowired
    NotService nots;
	
	private double getSoldByLot(WinLots wl) {
		// TODO Auto-generated method stub
		ModeGame mg = wl.getMg();
		double sold = 0;
		if(mg.getWin().contains("/")) {
			String [] sdt = mg.getWin().split("/");
			
			for(String st : sdt) {
				String [] dt = st.split("\\=");
				if(dt[0].equals(wl.getName())) {
					double s = Double.parseDouble(dt[1]);
					sold = ((s*wl.getBc().getMontant()) / mg.getPoint_per_price());
					break;
				}
			}
		} else {
			String [] dt = mg.getWin().split("\\=");
			double s = Double.parseDouble(dt[1]);
		    sold = ((s*wl.getBc().getMontant()) / mg.getPoint_per_price());
		}
		return sold;
	}
   
	public Game getGame(Long cm,List<Game> lmg){
		for(Game  g : lmg) {
				if(g.getId() == cm) {
					return g;
				}
		}
		return null;
	}
	
	public ModeGame getMG(String cm,Game g){
			for(ModeGame mg : g.getModegames() ) {
				if(mg.getCode().equals(cm)) {
					return mg;
				}
			}
		return null;
	}
    
	@Data
	public class VResp{
		boolean crash;
		List<WinLots> winlot;
		int code;
		List<FreeWinLots>  freelot;
		private double sold;
		String msg;
		public VResp(List<WinLots> lwl, TicketClient tc) {
			super();
			this.winlot = lwl;
			this.ticket = tc;
			crash = false;
		}
		
		public VResp(List<WinLots> lwl, TicketClient tc, List<FreeWinLots>  fwl, double s) {
			super();
			this.winlot = lwl;
			this.ticket = tc;
			crash = false;
			this.freelot = fwl;
			this.sold = s;
		}
		public VResp() {}
		public VResp(TicketClient tk,String m,  int c) {
			this.ticket = tk;
			crash = true;
			code = c;
			msg = m;
			
		}
		TicketClient ticket;
	}


	public JwtResponse getWinToDay() {
		// TODO Auto-generated method stub
		List<GameMaster> mgs = mstgame.getActiveGames();
		List<GameWin> gw = new ArrayList<>();
		for(GameMaster mg : mgs) {
			Optional<Sold> s  = ticketc.getTotalSoldTicketToDay(mg.getId());
			if(s.isPresent() && s.get().getSold()>0) {
			double ts = s.get().getSold();
			gw.add(new GameWin(mg.getCode(), ts,mg.getId()));
			} else {
		    gw.add(new GameWin(mg.getCode(), 0, mg.getId()));
			}
		}
		
		return new JwtResponse<List<GameWin>>(false,gw,"Siksè");
	}
	
	public JwtResponse getWinGlobal() {
		// TODO Auto-generated method stub
				List<GameMaster> mgs = mstgame.getActiveGames();
				List<GameWin> gw = new ArrayList<>();
				
				for(GameMaster mg : mgs) {
					Optional<Sold> s = ticketc.getTotalSoldTicketGlobal(mg.getId());
					if(s.isPresent() && s.get().getSold()>0) {
					  double ts = s.get().getSold();
					  gw.add(new GameWin(mg.getCode(), ts, mg.getId()));
					} else {
						gw.add(new GameWin(mg.getCode(),0,mg.getId()));	
					}
				}
				
				return new JwtResponse<List<GameWin>>(false,gw,"Siksè");
	}
	
	
	
	@Data
	class GameWin{
		String game;
		double  sold;
		Long id_game;
		public GameWin(String game, double sold, Long id) {
			super();
			this.game = game;
			this.sold = sold;
			this.id_game = id;
		}
		
	}


	public JwtResponse getLostToDay() {
		List<GameMaster> mgs = mstgame.getActiveGames();
		List<GameWin> gw = new ArrayList<>();
		for(GameMaster mg : mgs) {
			Optional<Sold> s  = ticketc.getTotalLostSoldTicketToDay(mg.getId());
			if(s.isPresent() && s.get().getSold()>0) {
			double ts = s.get().getSold();
			 gw.add(new GameWin(mg.getCode(), ts, mg.getId()));
			} else {
				gw.add(new GameWin(mg.getCode(), 0, mg.getId()));
			}
		}
		
		return new JwtResponse<List<GameWin>>(false,gw,"Siksè");
	}


	public JwtResponse getLostGlobal() {
		// TODO Auto-generated method stub
		List<GameMaster> mgs = mstgame.getActiveGames();
		List<GameWin> gw = new ArrayList<>();
		
		for(GameMaster mg : mgs) {
			Optional<Sold> s = ticketc.getTotalLostSoldTicketGlobal(mg.getId());
			if(s.isPresent() && s.get().getSold()>0) {
			   double ts = s.get().getSold();
			    gw.add(new GameWin(mg.getCode(), ts, mg.getId()));
			} else {
				gw.add(new GameWin(mg.getCode(),0, mg.getId()));	
			}
		}
		
		return new JwtResponse<List<GameWin>>(false,gw,"Siksè");
	}


	public JwtResponse getTicketOfTodayByGame(Long game) {
		// TODO Auto-generated method stub
		List<TicketClient> tc = ticketc.getTicketOfTodayByGame(game);
		return new JwtResponse<List<TicketClient>>(false,tc,"Siksè");
	}


	public JwtResponse getTicketByGame(Long game, int day, int s) {
		List<TicketClient> tc ;
		if(s==0) {
		      tc = ticketc.getTicketByGame(game,day);
		}else if (s==1) {
			  tc = ticketc.getTicketByGameWin(game,day);
		}else {
			  tc = ticketc.getTicketByGameLost(game,day);
		}
		return new JwtResponse<List<TicketClient>>(false,tc,"Siksè");
	}


	public JwtResponse getTicketOfTodayByGame(Long game, boolean b) {
		 List<TicketClient> tc;
		if(b) {
		     tc = ticketc.getTicketOfTodayByGameWin(game);
		 } else {
			 tc = ticketc.getTicketOfTodayByGameLost(game);
		}
		return new JwtResponse<List<TicketClient>>(false,tc,"Siksè");
	}

}