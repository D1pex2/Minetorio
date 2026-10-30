local function emit(t) localised_print('PUMP10 '..game.table_to_json(t)) end
local function ent(e) if not e or not e.valid then return nil end return {name=e.name,type=e.type,unit=e.unit_number,position=e.position,direction=e.direction} end
local function liquid(e) if not e or not e.valid then return nil end return e.fluidbox[1] end
local function links(p)
 local owners={};local pcs={}
 for _,b in ipairs(p.fluidbox.get_connections(1)) do owners[#owners+1]=ent(b.owner) end
 for _,c in ipairs(p.fluidbox.get_pipe_connections(1)) do pcs[#pcs+1]={flow=c.flow_direction,position=c.position,target_position=c.target_position,target=c.target and ent(c.target.owner),target_index=c.target_fluidbox_index,target_connection=c.target_pipe_connection_index} end
 return {owners=owners,ports=pcs}
end
local function train()
 local t=global.loco.train
 return {speed=t.speed,state=t.state,station=ent(t.station),manual=t.manual_mode,loco=ent(global.loco),wagon=ent(global.wagon),wagon_orientation=global.wagon.orientation,wagon_fluid=liquid(global.wagon),target=ent(t.path_end_rail)}
end
local function snapshot(phase,tick)
 local a={};for i,c in ipairs(global.rigs or {}) do local p=c.pump
  a[#a+1]={index=c.index,pump=ent(p),source=ent(c.source),pump_fluid=liquid(p),source_fluid=liquid(c.source),energy=p.energy,active=p.active,status=p.status,power_source_active=c.power.valid and c.power.active or false,pump_network=p.electric_network_id,power_network=c.power.valid and c.power.electric_network_id or nil,outflow=global.outflows and global.outflows[c.index],rail_target=ent(p.pump_rail_target),links=links(p)}
 end
 emit({kind='snapshot',phase=phase,tick=tick,phase_tick=tick-global.phase_start,train=train(),rigs=a,simultaneous_flows=global.flow_count})
end
local function cleanup()
 for _,r in ipairs(global.rigs or {}) do for _,e in ipairs({r.pump,r.source,r.pole,r.power}) do if e.valid then e.destroy() end end end
 global.rigs={}
end
local function setup_rig(index)
 local s=global.surface; local pos=global.wagon.position; local side=index<=3 and -1 or 1; local slot=(index-1)%3-1
 local p=s.create_entity{name='pump',position={pos.x+side*2,pos.y+slot*2},direction=side<0 and defines.direction.east or defines.direction.west,force='player'}
 if not p then emit({kind='rig_error',index=index,what='pump'});return end
 local z=p.position
 local source=s.create_entity{name='oracle-fluid-source',position={z.x+(side<0 and -2 or 1),z.y},force='player'}
 local pole=s.create_entity{name='oracle-isolated-pole',position={z.x,z.y},force='player'}
 local power=s.create_entity{name='electric-energy-interface',position={z.x,z.y},force='player'}
 if not source or not pole or not power then emit({kind='rig_error',index=index,pump=ent(p),source=ent(source),pole=ent(pole),power=ent(power)});return end
 power.power_production=1000000;source.fluidbox[1]={name='water',amount=5000,temperature=15}
 global.rigs[#global.rigs+1]={index=index,pump=p,source=source,pole=pole,power=power}
 emit({kind='rig',index=index,side=side,slot=slot,pump=ent(p),source=ent(source),pole=ent(pole),power=ent(power),source_capacity=source.fluidbox.get_capacity(1),pump_capacity=p.fluidbox.get_capacity(1),links=links(p)})
end
local function amount(e) local f=liquid(e);return f and f.amount or 0 end
local function track_flow()
 global.previous=global.previous or {};global.outflows={};global.flow_count=0
 for _,r in ipairs(global.rigs or {}) do
  local sum=amount(r.source)+amount(r.pump);local old=global.previous[r.index];local out=old and old-sum or 0
  global.outflows[r.index]=out;global.previous[r.index]=sum
  if out>0.0001 then global.flow_count=global.flow_count+1;if global.phase==7 then global.seen=global.seen or {};global.seen[r.index]=true end end
 end
end
local function reset_buffers(tick)
 local before={};for _,r in ipairs(global.rigs) do before[#before+1]={index=r.index,source=liquid(r.source),pump=liquid(r.pump)} end
 emit({kind='controlled_reset',phase=global.phase,tick=tick,before_wagon=liquid(global.wagon),before=before})
 global.wagon.fluidbox[1]=nil
 for _,r in ipairs(global.rigs) do r.source.fluidbox[1]={name='water',amount=5000,temperature=15} end
 global.previous={}
end
local function begin_phase(phase,tick)
 global.phase=phase;global.phase_start=tick;global.previous={};global.outflows={};global.flow_count=0
 if phase<=6 then cleanup();global.wagon.fluidbox[1]=nil;setup_rig(phase)
 elseif phase==7 then cleanup();global.wagon.fluidbox[1]=nil;for i=1,6 do setup_rig(i) end
 elseif phase==8 then
  local selected=global.selected
  for _,r in ipairs(global.rigs) do r.source.fluidbox[1]={name='water',amount=5000,temperature=15};r.pump.fluidbox[1]=nil end;global.wagon.fluidbox[1]=nil
  if selected then for _,r in ipairs(global.rigs) do if r.index==selected then r.power.destroy();r.pump.energy=0 end end end
 elseif phase==9 then
  for _,r in ipairs(global.rigs) do if not r.power.valid then r.power=global.surface.create_entity{name='electric-energy-interface',position=r.pump.position,force='player'};r.power.power_production=1000000 end;r.source.fluidbox[1]={name='water',amount=5000,temperature=15};r.pump.fluidbox[1]=nil end;global.wagon.fluidbox[1]=nil
 elseif phase==10 then
  cleanup();global.wagon.fluidbox[1]=nil;global.loco.train.manual_mode=true;setup_rig(global.positive or 1)
 elseif phase==11 then
  global.rail=global.station.connected_rail;global.station.destroy();cleanup();global.wagon.fluidbox[1]=nil;setup_rig(global.positive or 1)
 elseif phase==12 then
  cleanup();global.wagon.fluidbox[1]=nil
  local t=global.loco.train;t.schedule={current=1,records={{rail=global.rail,temporary=true,wait_conditions={{type='time',compare_type='and',ticks=12000}}}}};t.manual_mode=false;global.awaiting_temporary=true
 elseif phase==13 then
  cleanup();global.wagon.fluidbox[1]=nil;local t=global.loco.train;t.schedule=nil;t.manual_mode=false;setup_rig(global.positive or 1)
 end
 emit({kind='phase_start',phase=phase,tick=tick,selected=global.selected,positive=global.positive,train=train(),controlled_reset='wagon and/or pump/source fluid reset at phase boundary; see code'});snapshot(phase,tick)
end
script.on_init(function()
 local s=game.create_surface('pump9',{seed=12345,water='none',starting_area='none',autoplace_controls={['enemy-base']={frequency='none',size='none',richness='none'}}});global.surface=s
 for y=-400,96,32 do s.request_to_generate_chunks({0,y},1) end;s.force_generate_chunk_requests()
 local n=0;for y=-350,60,2 do if s.create_entity{name='straight-rail',position={0,y},direction=defines.direction.north,force='player'} then n=n+1 end end
 local l=s.create_entity{name='locomotive',position={0,0},direction=defines.direction.north,force='player'}
 local w=s.create_entity{name='fluid-wagon',position={0,7},direction=defines.direction.north,force='player'}
 local st=s.create_entity{name='train-stop',position={2,-300},direction=defines.direction.north,force='player'}
 if not l or not w or not st then emit({kind='setup_error',loco=l~=nil,wagon=w~=nil,station=st~=nil,rails=n});return end
 st.backer_name='Pump9';l.get_fuel_inventory().insert{name='coal',count=30};global.loco=l;global.wagon=w;global.station=st;global.rigs={};global.phase=0
 emit({kind='setup',rails=n,loco=ent(l),wagon=ent(w),station=ent(st),station_rail=ent(st.connected_rail),weight=l.train.weight,car_count=#l.train.carriages,pump_prototype='vanilla',source_prototype='oracle-fluid-source',power_prototype='oracle-isolated-pole'})
end)
script.on_event(defines.events.on_tick,function(e)
 if not global.loco then return end
 if e.tick==0 then local t=global.loco.train;t.schedule={current=1,records={{station='Pump9',wait_conditions={{type='time',compare_type='and',ticks=12000}}}}};t.manual_mode=false end
 if global.phase==0 then
  if e.tick%60==0 then emit({kind='arrival_trace',tick=e.tick,train=train()}) end
  if global.loco.train.state==defines.train_state.wait_station and global.loco.train.station and global.loco.train.speed==0 then emit({kind='native_arrival',tick=e.tick,train=train()});begin_phase(1,e.tick) end
  if e.tick==1000 and global.phase==0 then emit({kind='arrival_failed',tick=e.tick,train=train()});global.phase=99 end
 elseif global.phase<99 then
  if global.phase==12 and global.awaiting_temporary and global.loco.train.state==defines.train_state.wait_station then
   global.awaiting_temporary=false;global.phase_start=e.tick;setup_rig(global.positive or 1);emit({kind='fresh_temporary_ready',tick=e.tick,train=train()})
  end
  if global.phase==8 and global.selected then for _,r in ipairs(global.rigs) do if r.index==global.selected then r.pump.energy=0 end end end
  local delta=e.tick-global.phase_start
  if global.phase>=7 and global.phase<=9 then track_flow() end
  if delta>0 and (global.phase>=7 and global.phase<=9 or delta<=150 or delta%5==0 or delta>=180) then snapshot(global.phase,e.tick) end
  if global.phase>=7 and global.phase<=9 and delta>0 and delta<240 and delta%15==0 then reset_buffers(e.tick) end
  if delta>=240 then
   local t=train(); local results={};for _,r in ipairs(global.rigs) do local f=liquid(r.source);results[#results+1]={index=r.index,source_amount=f and f.amount or 0,pump_energy=r.pump.energy,active=r.pump.active,links=links(r.pump)} end
   emit({kind='phase_end',phase=global.phase,tick=e.tick,train=t,rigs=results})
   if global.phase<=6 and t.wagon_fluid and t.wagon_fluid.amount>0 then global.positive=global.positive or global.phase end
   if global.phase==7 then for i=1,6 do if global.seen and global.seen[i] and not global.selected then global.selected=i end end end
   if global.phase==13 then global.phase=99;emit({kind='done',tick=e.tick}) else begin_phase(global.phase+1,e.tick) end
  end
 end
end)
